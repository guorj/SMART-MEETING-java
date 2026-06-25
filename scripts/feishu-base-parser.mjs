import fs from 'fs';
import zlib from 'zlib';

/** 飞书 Bitable 离线 .base 字段 ID（综合管理事项代办清单） */
export const FIELD = {
  TASK_NAME: 'fldO8jwfSy',
  CREATE_TIME: 'fldS52UyHF',
  DUE_DATE: 'fldl33zx7C',
  DONE: 'fld76NRu00',
  CATEGORY: 'fldaR9enAV',
  ASSIGNEES: 'fldqVXMJ5f',
  LONG_TERM: 'fldB1teYEz',
};

/**
 * 从 gzipSnapshot 原始 JSON 字符串提取 tenantID（避免 JS number 精度丢失）。
 * @param {string} snapshotJson
 * @returns {string|null}
 */
export function extractTenantIdFromSnapshotJson(snapshotJson) {
  const m = snapshotJson.match(/"tenantID"\s*:\s*(\d+)/);
  return m ? m[1] : null;
}

/**
 * @param {unknown} cell
 * @returns {string}
 */
export function parseTextCell(cell) {
  if (cell == null) return '';
  const v = cell.value;
  if (v == null) return '';
  if (typeof v === 'string') return v.trim();
  if (Array.isArray(v)) {
    return v
      .map((part) => (part && typeof part.text === 'string' ? part.text : ''))
      .join('')
      .trim();
  }
  return String(v).trim();
}

/**
 * @param {unknown} cell
 * @returns {string|null} yyyy-MM-dd
 */
export function parseDateCell(cell) {
  if (cell == null || cell.value == null) return null;
  const ms = Number(cell.value);
  if (!Number.isFinite(ms)) return null;
  const d = new Date(ms);
  if (Number.isNaN(d.getTime())) return null;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * @param {unknown} cell
 * @returns {boolean}
 */
export function parseCheckboxCell(cell) {
  return cell != null && cell.value === true;
}

/**
 * @param {unknown} cell
 * @returns {string[]}
 */
export function parseUserCell(cell) {
  if (cell == null || cell.value == null) return [];
  const users = cell.value.users;
  if (!Array.isArray(users)) return [];
  return users
    .map((u) => (u && u.name ? String(u.name).trim() : ''))
    .filter(Boolean);
}

/**
 * @param {Record<string, { name?: string, property?: { options?: Array<{ id: string, name: string }> } }>} fieldMap
 * @returns {Map<string, string>} optionId -> label
 */
export function buildOptionMap(fieldMap) {
  const map = new Map();
  for (const field of Object.values(fieldMap || {})) {
    const options = field?.property?.options;
    if (!Array.isArray(options)) continue;
    for (const opt of options) {
      if (opt?.id && opt.name != null) {
        map.set(opt.id, String(opt.name).trim());
      }
    }
  }
  return map;
}

/**
 * @param {unknown} cell
 * @param {Map<string, string>} optionMap
 * @returns {string}
 */
export function parseSelectCell(cell, optionMap) {
  if (cell == null || cell.value == null) return '';
  const raw = String(cell.value).trim();
  return optionMap.get(raw) || raw;
}

/**
 * @param {boolean} done
 * @param {string|null} plannedEndDate
 * @param {string} [todayStr] yyyy-MM-dd，测试注入用
 * @returns {number} 0-3
 */
export function deriveStatus(done, plannedEndDate, todayStr) {
  if (done) return 2;
  const today = todayStr || formatTodayLocal();
  if (plannedEndDate && plannedEndDate < today) return 3;
  return 1;
}

/**
 * @returns {string} yyyy-MM-dd（本地时区）
 */
export function formatTodayLocal() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * @param {string} recId
 * @param {string[]} assigneeNames
 * @param {string} longTerm
 * @returns {string}
 */
export function buildRemark(recId, assigneeNames, longTerm) {
  const parts = [`[feishu:recId=${recId}]`];
  if (assigneeNames.length > 1) {
    parts.push(`共同执行人：${assigneeNames.slice(1).join('、')}`);
  }
  if (longTerm) {
    parts.push(`长期任务：${longTerm}`);
  }
  let remark = parts.join(' ');
  if (remark.length > 500) {
    remark = remark.slice(0, 497) + '...';
  }
  return remark;
}

/**
 * @param {string} recId
 * @param {Record<string, unknown>} cells
 * @param {Record<string, { name?: string }>} fieldMap
 * @param {Map<string, string>} optionMap
 * @param {{ tenantId: string, todayStr?: string }} ctx
 * @returns {import('./feishu-base-parser.mjs').NormalizedTaskRow}
 */
export function normalizeRecord(recId, cells, fieldMap, optionMap, ctx) {
  const taskName = parseTextCell(cells[FIELD.TASK_NAME]);
  const startDate = parseDateCell(cells[FIELD.CREATE_TIME]);
  const plannedEndDate = parseDateCell(cells[FIELD.DUE_DATE]);
  const done = parseCheckboxCell(cells[FIELD.DONE]);
  const businessBlock = parseSelectCell(cells[FIELD.CATEGORY], optionMap);
  const assigneeNames = parseUserCell(cells[FIELD.ASSIGNEES]);
  const longTerm = parseTextCell(cells[FIELD.LONG_TERM]);
  const status = deriveStatus(done, plannedEndDate, ctx.todayStr);
  const progress = done ? 100 : 0;

  return {
    feishuRecId: recId,
    taskName: taskName.slice(0, 200),
    businessBlock: businessBlock.slice(0, 32),
    startDate,
    plannedEndDate,
    status,
    progress,
    assigneeNames,
    remark: buildRemark(recId, assigneeNames, longTerm),
    tenantId: ctx.tenantId,
    projectId: null,
  };
}

/**
 * @param {string} filePath
 * @returns {{ tenantId: string, fieldMap: Record<string, unknown>, records: import('./feishu-base-parser.mjs').NormalizedTaskRow[], meta: { recordCount: number, baseName: string } }}
 */
export function parseFeishuBase(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  const wrapper = JSON.parse(raw);
  if (!wrapper.gzipSnapshot) {
    throw new Error(`Invalid .base file (missing gzipSnapshot): ${filePath}`);
  }
  const snapshotJson = zlib.gunzipSync(Buffer.from(wrapper.gzipSnapshot, 'base64')).toString('utf8');
  const snapshot = JSON.parse(snapshotJson);
  const schema = snapshot[0]?.schema;
  if (!schema?.data?.table || !schema?.data?.recordMap) {
    throw new Error('Invalid snapshot: missing schema.data.table or recordMap');
  }

  const table = schema.data.table;
  const recordMap = schema.data.recordMap;
  const fieldMap = table.fieldMap || {};
  const optionMap = buildOptionMap(fieldMap);
  const tenantFromJson = extractTenantIdFromSnapshotJson(snapshotJson);
  const tenantFromSchema = schema.base?.tenantID != null ? String(schema.base.tenantID) : null;
  const tenantId = tenantFromJson || tenantFromSchema || '';

  if (!tenantId) {
    throw new Error('Could not resolve tenantID from .base snapshot');
  }

  const records = [];
  for (const [recId, cells] of Object.entries(recordMap)) {
    const row = normalizeRecord(recId, cells, fieldMap, optionMap, { tenantId });
    if (!row.taskName) continue;
    records.push(row);
  }

  return {
    tenantId,
    fieldMap,
    records,
    meta: {
      recordCount: records.length,
      baseName: schema.base?.name || '',
      rawRecordCount: Object.keys(recordMap).length,
    },
  };
}

/**
 * @typedef {Object} NormalizedTaskRow
 * @property {string} feishuRecId
 * @property {string} taskName
 * @property {string} businessBlock
 * @property {string|null} startDate
 * @property {string|null} plannedEndDate
 * @property {number} status
 * @property {number} progress
 * @property {string[]} assigneeNames
 * @property {string} remark
 * @property {string} tenantId
 * @property {null} projectId
 */
