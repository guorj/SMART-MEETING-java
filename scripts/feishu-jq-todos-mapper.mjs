import { buildFeishuRecIdRemark, buildFeishuSubtaskRemark } from './feishu-base-parser.mjs';

export const IMPORT_CREATOR = 'import-feishu-base-jq-todos';

const INT_MAX = 2147483647;

/**
 * jq_todos_subtask.tenant_id 为 int；超大 tenant 写 0。
 * @param {string|number} tenantId
 * @returns {number|string}
 */
export function tenantIdForTask(tenantId) {
  return tenantId;
}

/**
 * @param {string|number} tenantId
 * @returns {number}
 */
export function tenantIdForSubtask(tenantId) {
  const n = Number(tenantId);
  if (!Number.isFinite(n) || n > INT_MAX || n < -INT_MAX) {
    return 0;
  }
  return Math.trunc(n);
}

/**
 * @param {import('./feishu-base-parser.mjs').NormalizedTaskRow} row
 * @returns {object}
 */
export function rowToTaskPayload(row) {
  return {
    task_name: row.taskName,
    business_block: row.businessBlock || '未分类',
    project_id: row.projectId,
    decision_maker_user_id: 0,
    progress: row.progress,
    start_date: row.startDate || row.plannedEndDate || '1970-01-01',
    planned_end_date: row.plannedEndDate || row.startDate || '1970-01-01',
    status: row.status,
    task_detail: row.longTerm || null,
    remark: buildFeishuRecIdRemark(row.feishuRecId),
    deleted: 0,
    tenant_id: tenantIdForTask(row.tenantId),
    creator: IMPORT_CREATOR,
    updater: IMPORT_CREATOR,
  };
}

/**
 * @param {import('./feishu-base-parser.mjs').NormalizedTaskRow} row
 * @param {number} parentId
 * @param {number} subIndex
 * @param {number|null} assigneeId
 * @returns {object}
 */
export function rowToSubtaskPayload(row, parentId, subIndex, assigneeId) {
  return {
    parent_id: parentId,
    task_name: row.taskName,
    asignee_id: assigneeId ?? 0,
    remark: buildFeishuSubtaskRemark(row.feishuRecId, subIndex),
    deleted: 0,
    tenant_id: tenantIdForSubtask(row.tenantId),
    creator: null,
    updater: null,
  };
}

/**
 * @param {import('./feishu-base-parser.mjs').NormalizedTaskRow} row
 * @returns {number}
 */
export function expectedSubtaskCount(row) {
  return row.assigneeNames?.length || 0;
}
