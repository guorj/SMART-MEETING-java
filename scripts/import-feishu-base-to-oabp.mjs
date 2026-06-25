#!/usr/bin/env node
/**
 * 飞书离线 .base → oabp.jq_project_task_tracking 一次性/幂等导入。
 *
 * 用法：
 *   node import-feishu-base-to-oabp.mjs --file "F:/obsidian/Projects/xxx.base" --dry-run
 *   node import-feishu-base-to-oabp.mjs --file "..." --tenant-id 7252204669903667202
 */
import mysql from 'mysql2/promise';
import { parseFeishuBase } from './feishu-base-parser.mjs';

const IMPORT_CREATOR = 'import-feishu-base';
const DEFAULT_BASE_FILE = 'F:/obsidian/Projects/📋综合管理事项代办清单.base';

/**
 * @param {string[]} argv
 */
function parseArgs(argv) {
  const opts = {
    file: process.env.FEISHU_BASE_FILE || DEFAULT_BASE_FILE,
    dryRun: false,
    tenantId: process.env.IMPORT_TENANT_ID || null,
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dry-run') opts.dryRun = true;
    else if (a === '--file' && argv[i + 1]) opts.file = argv[++i];
    else if (a === '--tenant-id' && argv[i + 1]) opts.tenantId = argv[++i];
    else if (a === '--help' || a === '-h') {
      printHelp();
      process.exit(0);
    }
  }
  return opts;
}

function printHelp() {
  console.log(`Usage: node import-feishu-base-to-oabp.mjs [options]

Options:
  --file <path>       Feishu .base file (default: FEISHU_BASE_FILE env or built-in path)
  --tenant-id <id>    Override tenant_id (default: from .base snapshot)
  --dry-run           Parse and preview only, no DB writes
  --help              Show this help

Environment (oabp MySQL, same as application.yml):
  DB_HOST, DB_PORT, DB_OABP_NAME, DB_USERNAME, DB_PASSWORD
`);
}

/**
 * @returns {Promise<import('mysql2/promise').Connection>}
 */
async function connectOabp() {
  return mysql.createConnection({
    host: process.env.DB_HOST || '60.205.1.17',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USERNAME || 'intelligence',
    password: process.env.DB_PASSWORD || 'intelligence@2026',
    database: process.env.DB_OABP_NAME || 'oabp',
    timezone: '+08:00',
  });
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {string} nickname
 * @returns {Promise<number|null>}
 */
async function resolveAssigneeId(conn, nickname) {
  if (!nickname) return null;
  const [rows] = await conn.query(
    'SELECT id FROM system_users WHERE nickname = ? AND (deleted = 0 OR deleted IS NULL) LIMIT 1',
    [nickname],
  );
  if (!rows.length) return null;
  return Number(rows[0].id);
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {string} recId
 * @returns {Promise<number|null>}
 */
async function findExistingIdByRecId(conn, recId) {
  const prefix = `[feishu:recId=${recId}]%`;
  const [rows] = await conn.query(
    'SELECT id FROM jq_project_task_tracking WHERE remark LIKE ? LIMIT 1',
    [prefix],
  );
  if (!rows.length) return null;
  return Number(rows[0].id);
}

/**
 * @param {import('./feishu-base-parser.mjs').NormalizedTaskRow} row
 * @param {number|null} assigneeUserId
 */
function rowToDbPayload(row, assigneeUserId) {
  return {
    task_name: row.taskName,
    business_block: row.businessBlock || '未分类',
    project_id: row.projectId,
    assignee_user_id: assigneeUserId ?? 0,
    progress: row.progress,
    start_date: row.startDate || row.plannedEndDate || '1970-01-01',
    planned_end_date: row.plannedEndDate || row.startDate || '1970-01-01',
    status: row.status,
    remark: row.remark,
    deleted: 0,
    tenant_id: row.tenantId,
    creator: IMPORT_CREATOR,
    updater: IMPORT_CREATOR,
  };
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {number} id
 * @param {ReturnType<typeof rowToDbPayload>} payload
 */
async function updateRow(conn, id, payload) {
  await conn.query(
    `UPDATE jq_project_task_tracking SET
      task_name = ?, business_block = ?, project_id = ?, assignee_user_id = ?,
      progress = ?, start_date = ?, planned_end_date = ?, status = ?,
      remark = ?, deleted = ?, tenant_id = ?, updater = ?, update_time = NOW()
     WHERE id = ?`,
    [
      payload.task_name,
      payload.business_block,
      payload.project_id,
      payload.assignee_user_id,
      payload.progress,
      payload.start_date,
      payload.planned_end_date,
      payload.status,
      payload.remark,
      payload.deleted,
      payload.tenant_id,
      payload.updater,
      id,
    ],
  );
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {ReturnType<typeof rowToDbPayload>} payload
 */
async function insertRow(conn, payload) {
  await conn.query(
    `INSERT INTO jq_project_task_tracking (
      task_name, business_block, project_id, assignee_user_id,
      progress, start_date, planned_end_date, status, remark,
      deleted, tenant_id, creator, create_time, updater, update_time
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW())`,
    [
      payload.task_name,
      payload.business_block,
      payload.project_id,
      payload.assignee_user_id,
      payload.progress,
      payload.start_date,
      payload.planned_end_date,
      payload.status,
      payload.remark,
      payload.deleted,
      payload.tenant_id,
      payload.creator,
      payload.updater,
    ],
  );
}

/**
 * @param {import('./feishu-base-parser.mjs').NormalizedTaskRow[]} records
 * @param {boolean} dryRun
 */
async function importRecords(records, dryRun) {
  const stats = { inserted: 0, updated: 0, skipped: 0, assigneeMiss: [] };
  /** @type {import('mysql2/promise').Connection|null} */
  let conn = null;

  if (!dryRun) {
    conn = await connectOabp();
  }

  try {
    for (const row of records) {
      const primaryAssignee = row.assigneeNames[0] || null;
      let assigneeUserId = null;

      if (primaryAssignee && conn) {
        assigneeUserId = await resolveAssigneeId(conn, primaryAssignee);
        if (assigneeUserId == null) {
          stats.assigneeMiss.push({ recId: row.feishuRecId, name: primaryAssignee, task: row.taskName });
        }
      } else if (primaryAssignee && dryRun) {
        assigneeUserId = null;
      }

      const payload = rowToDbPayload(row, assigneeUserId);

      if (dryRun) {
        stats.inserted++;
        continue;
      }

      const existingId = await findExistingIdByRecId(conn, row.feishuRecId);
      if (existingId != null) {
        await updateRow(conn, existingId, payload);
        stats.updated++;
      } else {
        await insertRow(conn, payload);
        stats.inserted++;
      }
    }
  } finally {
    if (conn) await conn.end();
  }

  return stats;
}

async function main() {
  const opts = parseArgs(process.argv);
  console.log(`Parsing: ${opts.file}`);
  const parsed = parseFeishuBase(opts.file);
  if (opts.tenantId) {
    for (const r of parsed.records) {
      r.tenantId = opts.tenantId;
    }
  }

  console.log(`Base: ${parsed.meta.baseName}`);
  console.log(`Tenant: ${parsed.tenantId}${opts.tenantId ? ' (CLI override)' : ''}`);
  console.log(`Records: ${parsed.records.length} (raw recordMap: ${parsed.meta.rawRecordCount})`);

  if (opts.dryRun) {
    console.log('\n--- dry-run preview (first 3) ---');
    for (const r of parsed.records.slice(0, 3)) {
      console.log(JSON.stringify({
        recId: r.feishuRecId,
        taskName: r.taskName,
        businessBlock: r.businessBlock,
        assignees: r.assigneeNames,
        startDate: r.startDate,
        plannedEndDate: r.plannedEndDate,
        status: r.status,
        progress: r.progress,
        remark: r.remark,
      }, null, 2));
    }
  }

  const stats = await importRecords(parsed.records, opts.dryRun);

  console.log('\n--- result ---');
  if (opts.dryRun) {
    console.log(`Would upsert: ${stats.inserted} rows`);
  } else {
    console.log(`Inserted: ${stats.inserted}`);
    console.log(`Updated: ${stats.updated}`);
    console.log(`Skipped: ${stats.skipped}`);
  }
  if (stats.assigneeMiss.length) {
    console.log(`Assignee miss (${stats.assigneeMiss.length}):`);
    for (const m of stats.assigneeMiss.slice(0, 20)) {
      console.log(`  - ${m.name} (${m.recId}) ${m.task.slice(0, 40)}`);
    }
    if (stats.assigneeMiss.length > 20) {
      console.log(`  ... and ${stats.assigneeMiss.length - 20} more`);
    }
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
