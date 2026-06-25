import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import {
  parseFeishuBase,
  deriveStatus,
  parseTextCell,
  parseDateCell,
  parseCheckboxCell,
  parseUserCell,
  buildRemark,
  extractTenantIdFromSnapshotJson,
  FIELD,
} from './feishu-base-parser.mjs';

const BASE_FILE =
  process.env.FEISHU_BASE_FILE ||
  'F:/obsidian/Projects/📋综合管理事项代办清单.base';

const fileExists = fs.existsSync(BASE_FILE);

describe('feishu-base-parser', () => {
  it('deriveStatus: done -> 2', () => {
    assert.equal(deriveStatus(true, '2025-01-01', '2026-06-24'), 2);
  });

  it('deriveStatus: not done + past due -> 3', () => {
    assert.equal(deriveStatus(false, '2025-01-01', '2026-06-24'), 3);
  });

  it('deriveStatus: not done + future -> 1', () => {
    assert.equal(deriveStatus(false, '2027-01-01', '2026-06-24'), 1);
  });

  it('parseTextCell handles array segments', () => {
    assert.equal(parseTextCell({ value: [{ type: 'text', text: 'hello' }] }), 'hello');
  });

  it('parseDateCell converts epoch ms', () => {
    assert.equal(parseDateCell({ value: 1758816000000 }), '2025-09-26');
  });

  it('parseCheckboxCell', () => {
    assert.equal(parseCheckboxCell({ value: true }), true);
    assert.equal(parseCheckboxCell({ value: false }), false);
    assert.equal(parseCheckboxCell(null), false);
  });

  it('parseUserCell extracts names', () => {
    assert.deepEqual(
      parseUserCell({
        value: { users: [{ name: '管小慧' }, { name: '郭运娇' }] },
      }),
      ['管小慧', '郭运娇'],
    );
  });

  it('buildRemark includes feishu recId prefix', () => {
    const r = buildRemark('recABC', ['管小慧', '郭运娇'], '随时跟进');
    assert.ok(r.startsWith('[feishu:recId=recABC]'));
    assert.ok(r.includes('共同执行人：郭运娇'));
    assert.ok(r.includes('长期任务：随时跟进'));
  });

  it('extractTenantIdFromSnapshotJson preserves full integer', () => {
    const json = '{"base":{"tenantID":7252204669903667202}}';
    assert.equal(extractTenantIdFromSnapshotJson(json), '7252204669903667202');
  });

  it('parseFeishuBase loads 129 records from real .base file', { skip: !fileExists }, () => {
    const parsed = parseFeishuBase(BASE_FILE);
    assert.equal(parsed.records.length, 129);
    assert.equal(parsed.meta.rawRecordCount, 129);
    assert.ok(parsed.tenantId.length >= 10);
    assert.ok(parsed.meta.baseName.includes('综合管理'));

    const first = parsed.records[0];
    assert.ok(first.taskName.length > 0);
    assert.ok(first.remark.startsWith('[feishu:recId='));
    assert.ok([0, 1, 2, 3].includes(first.status));
    assert.ok(first.progress === 0 || first.progress === 100);
  });

  it('all records have task_name and remark prefix', { skip: !fileExists }, () => {
    const { records } = parseFeishuBase(BASE_FILE);
    for (const r of records) {
      assert.ok(r.taskName, `missing taskName for ${r.feishuRecId}`);
      assert.match(r.remark, /^\[feishu:recId=rec/);
    }
  });

  it('FIELD constants match known snapshot ids', () => {
    assert.equal(FIELD.TASK_NAME, 'fldO8jwfSy');
    assert.equal(FIELD.DONE, 'fld76NRu00');
  });
});
