#!/usr/bin/env node
/**
 * 标准操作：飞书 VC 妙记 URL → 下载音视频 → PCM → 讯飞 IST 离线全文转写。
 *
 * 用法:
 *   node scripts/vc-minute-offline-transcribe.mjs <minute_url_or_token> [options]
 *
 * 选项:
 *   --out-dir <path>       输出目录，默认 data/minutes/{token}
 *   --skip-download        跳过飞书下载（目录内已有 audio.* / *.pcm）
 *   --skip-feishu-transcript  不拉飞书原生 transcript.txt
 *   --skip-xfyun           仅下载 + 转 PCM，不跑讯飞
 *
 * 依赖: ffmpeg、Node 18+、scripts/npm install（mysql2）
 * 前置: Admin 已完成飞书 OAuth，DB 有 meeting.feishu.minutes.user-refresh-token
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { convertToPcm16k } from './lib/audio-convert.mjs';
import { downloadFeishuMinute, extractMinuteToken } from './lib/feishu-minutes.mjs';
import { formatTranscriptText, transcribeIstAudio } from './lib/xfyun-offline.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..');

function parseArgs(argv) {
  const positional = [];
  const flags = {
    outDir: '',
    skipDownload: false,
    skipFeishuTranscript: false,
    skipXfyun: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--out-dir') flags.outDir = argv[++i] || '';
    else if (a === '--skip-download') flags.skipDownload = true;
    else if (a === '--skip-feishu-transcript') flags.skipFeishuTranscript = true;
    else if (a === '--skip-xfyun') flags.skipXfyun = true;
    else if (a === '--help' || a === '-h') flags.help = true;
    else if (!a.startsWith('-')) positional.push(a);
  }
  return { positional, flags };
}

async function findAudioFile(dir, token) {
  for (const name of [`audio.m4a`, `audio.mp4`, `audio.wav`, `${token}.pcm`, 'audio.pcm']) {
    const p = path.join(dir, name);
    try {
      await fs.access(p);
      return p;
    } catch {
      // continue
    }
  }
  return null;
}

function printHelp() {
  console.log(`Usage: node scripts/vc-minute-offline-transcribe.mjs <minute_url_or_token> [options]

Options:
  --out-dir <path>            Output directory (default: data/minutes/{token})
  --skip-download             Use existing files in out-dir
  --skip-feishu-transcript    Skip Feishu native transcript export
  --skip-xfyun                Skip Xfyun IST full transcription

See docs/vc-minute-offline-transcribe.md`);
}

async function main() {
  const { positional, flags } = parseArgs(process.argv.slice(2));
  if (flags.help || !positional[0]) {
    printHelp();
    process.exit(flags.help ? 0 : 1);
  }

  const minuteToken = extractMinuteToken(positional[0]);
  if (!minuteToken) {
    console.error('invalid minute url or token');
    process.exit(1);
  }

  const outDir = path.resolve(
    REPO_ROOT,
    flags.outDir || path.join('data', 'minutes', minuteToken),
  );
  await fs.mkdir(outDir, { recursive: true });

  let title = '';
  let durationMs = 0;
  let audioPath = null;

  if (!flags.skipDownload) {
    console.log(`[1/4] download feishu minute ${minuteToken} -> ${outDir}`);
    const dl = await downloadFeishuMinute({
      minuteToken,
      outDir,
      includeFeishuTranscript: !flags.skipFeishuTranscript,
    });
    title = dl.title;
    durationMs = dl.durationMs;
    audioPath = dl.audioPath;
    console.log(`  title=${title || '(unknown)'} duration_ms=${durationMs}`);
    if (dl.feishuTranscriptPath) {
      console.log(`  feishu transcript: ${dl.feishuTranscriptPath}`);
    }
  } else {
    audioPath = await findAudioFile(outDir, minuteToken);
    try {
      const meta = JSON.parse(await fs.readFile(path.join(outDir, 'meta.json'), 'utf8'));
      const minute = meta?.data?.minute || {};
      title = minute.title || '';
      durationMs = Number(minute.duration || 0);
    } catch {
      // optional
    }
  }

  if (!audioPath) {
    audioPath = await findAudioFile(outDir, minuteToken);
  }
  if (!audioPath) {
    throw new Error('no audio file; run without --skip-download or place audio.m4a in out-dir');
  }

  const pcmPath = path.join(outDir, `${minuteToken}.pcm`);
  let pcmFile = audioPath.endsWith('.pcm') ? audioPath : pcmPath;

  if (!audioPath.endsWith('.pcm')) {
    console.log(`[2/4] ffmpeg -> ${pcmFile}`);
    pcmFile = await convertToPcm16k(audioPath, pcmPath);
    const stat = await fs.stat(pcmFile);
    console.log(`  pcm bytes=${stat.size}`);
  } else {
    console.log(`[2/4] use existing pcm ${pcmFile}`);
  }

  if (flags.skipXfyun) {
    console.log(`done (xfyun skipped) -> ${outDir}`);
    return;
  }

  console.log('[3/4] xfyun IST upload + poll (long audio may take several minutes)');
  const audio = await fs.readFile(pcmFile);
  const { upload, segments } = await transcribeIstAudio(audio, `${minuteToken}.pcm`, {
    onUpload: (u) => console.log(`  orderId=${u.orderId} estimateMs=${u.taskEstimateTimeMs}`),
    onPoll: ({ attempt, status }) => {
      if (attempt <= 5 || attempt % 6 === 0) {
        console.log(`  poll #${attempt} status=${status}`);
      }
    },
  });

  console.log(`[4/4] write transcript (${segments.length} segments)`);
  const jsonPath = path.join(outDir, 'xfyun-full.json');
  const txtPath = path.join(outDir, 'xfyun-full.transcript.txt');
  await fs.writeFile(
    jsonPath,
    JSON.stringify(
      {
        minuteToken,
        title,
        durationMs,
        orderId: upload.orderId,
        segmentCount: segments.length,
        segments,
      },
      null,
      2,
    ),
    'utf8',
  );
  await fs.writeFile(
    txtPath,
    formatTranscriptText(segments, { minuteToken, title }),
    'utf8',
  );

  console.log(`done -> ${outDir}`);
  console.log(`  ${txtPath}`);
  console.log(`  ${jsonPath}`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
