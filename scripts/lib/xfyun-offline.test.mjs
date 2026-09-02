import test from 'node:test';
import assert from 'node:assert/strict';
import { signIstParams, parseIstSegments, formatTranscriptText } from './xfyun-offline.mjs';

test('signIstParams encodes dateTime plus offset', () => {
  const sig = signIstParams(
    {
      accessKeyId: 'key',
      dateTime: '2026-06-07T20:29:05+0800',
      signatureRandom: 'abc',
    },
    'secret',
  );
  assert.equal(typeof sig, 'string');
  assert.ok(sig.length > 8);
});

test('parseIstSegments reads lattice json_1best', () => {
  const orderResult = JSON.stringify({
    lattice: [
      {
        json_1best: JSON.stringify({
          st: {
            bg: 1000,
            ed: 2500,
            rl: '2',
            rt: [{ ws: [{ cw: [{ w: '你好' }] }] }],
          },
        }),
      },
    ],
  });
  const segments = parseIstSegments(orderResult);
  assert.equal(segments.length, 1);
  assert.equal(segments[0].text, '你好');
  assert.equal(segments[0].speakerId, 'speaker_2');
});

test('formatTranscriptText includes header', () => {
  const text = formatTranscriptText(
    [{ startMs: 0, endMs: 1000, speakerId: 'speaker_1', text: '测试' }],
    { minuteToken: 'tok', title: '会议' },
  );
  assert.match(text, /minuteToken: tok/);
  assert.match(text, /\[00:00 - 00:01\]/);
});
