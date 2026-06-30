import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import {
  parseFeishuBase,
  buildFeishuRecIdRemark,
  buildFeishuSubtaskRemark,
} from './feishu-base-parser.mjs';
import {
  rowToTaskPayload,
  rowToSubtaskPayload,
  expectedSubtaskCount,
} from './feishu-jq-todos-mapper.mjs';

const BASE_FILE =
  process.env.FEISHU_BASE_FILE ||
  'F:/obsidian/Projects/📋综合管理事项代办清单.base';

const fileExists = fs.existsSync(BASE_FILE);

/** @returns {import('./feishu-base-parser.mjs').NormalizedTaskRow} */
function sampleRow(overrides = {}) {
  return {
    feishuRecId: 'recTEST',
    taskName: '测试任务',
    businessBlock: '综合管理',
    startDate: '2026-01-01',
    plannedEndDate: '2026-06-30',
    status: 1,
    progress: 0,
    assigneeNames: ['张三', '李四'],
    longTerm: '随时跟进',
    remark: '[feishu:recId=recTEST]',
    tenantId: '7252204669903667202',
    projectId: null,
    ...overrides,
  };
}

describe('feishu-jq-todos-mapper', () => {
  it('buildFeishuRecIdRemark', () => {
    assert.equal(buildFeishuRecIdRemark('recABC'), '[feishu:recId=recABC]');
  });

  it('buildFeishuSubtaskRemark', () => {
    assert.equal(buildFeishuSubtaskRemark('recABC', 1), '[feishu:recId=recABC:sub=1]');
  });

  it('rowToTaskPayload maps longTerm to task_detail and decision_maker 0 when unset', () => {
    const p = rowToTaskPayload(sampleRow());
    assert.equal(p.task_name, '测试任务');
    assert.equal(p.task_detail, '随时跟进');
    assert.equal(p.decision_maker_user_id, 0);
    assert.equal(p.remark, '[feishu:recId=recTEST]');
    assert.equal(p.business_block, '综合管理');
  });

  it('rowToTaskPayload empty longTerm -> null task_detail', () => {
    const p = rowToTaskPayload(sampleRow({ longTerm: '' }));
    assert.equal(p.task_detail, null);
  });

  it('expectedSubtaskCount matches assignees', () => {
    assert.equal(expectedSubtaskCount(sampleRow()), 2);
    assert.equal(expectedSubtaskCount(sampleRow({ assigneeNames: [] })), 0);
  });

  it('rowToSubtaskPayload uses asignee_id and remark index', () => {
    const p = rowToSubtaskPayload(sampleRow(), 99, 0, 158);
    assert.equal(p.parent_id, 99);
    assert.equal(p.asignee_id, 158);
    assert.equal(p.remark, '[feishu:recId=recTEST:sub=0]');
    assert.equal(p.creator, null);
  });

  it('rowToSubtaskPayload null assignee -> 0', () => {
    const p = rowToSubtaskPayload(sampleRow(), 1, 1, null);
    assert.equal(p.asignee_id, 0);
  });
});

describe('parseFeishuBase for jq_todos import', () => {
  it('records include longTerm field', { skip: !fileExists }, () => {
    const { records } = parseFeishuBase(BASE_FILE);
    assert.equal(records.length, 129);
    const withLong = records.filter((r) => r.longTerm && r.longTerm.length > 0);
    assert.ok(withLong.length >= 10);
    assert.ok(typeof records[0].longTerm === 'string');
  });

  it('total assignee subtask rows', { skip: !fileExists }, () => {
    const { records } = parseFeishuBase(BASE_FILE);
    const totalSubs = records.reduce((n, r) => n + expectedSubtaskCount(r), 0);
    assert.ok(totalSubs > 129, `expected >129 subtasks, got ${totalSubs}`);
  });

  it('tenantIdForSubtask overflows int -> 0', async () => {
    const { tenantIdForSubtask } = await import('./feishu-jq-todos-mapper.mjs');
    assert.equal(tenantIdForSubtask('7252204669903667202'), 0);
  });
});
