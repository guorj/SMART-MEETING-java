#!/usr/bin/env node
/**
 * 飞书离线 .base → oabp.jq_todos_task + jq_todos_subtask 一次性/幂等导入。
 *
 * 用法：
 *   node import-feishu-base-to-jq-todos.mjs --file "F:/obsidian/Projects/xxx.base" --dry-run
 *   node import-feishu-base-to-jq-todos.mjs --file "..." --tenant-id 7252204669903667202
 */
import mysql from 'mysql2/promise';
import { parseFeishuBase } from './feishu-base-parser.mjs';
import {
  IMPORT_CREATOR,
  rowToTaskPayload,
  rowToSubtaskPayload,
} from './feishu-jq-todos-mapper.mjs';
import { connectOabp, resolveAssigneeId, DEFAULT_OABP_DATABASE } from './oabp-db.mjs';

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
  console.log(`Usage: node import-feishu-base-to-jq-todos.mjs [options]

Options:
  --file <path>       Feishu .base file (default: FEISHU_BASE_FILE env or built-in path)
  --tenant-id <id>    Override tenant_id (default: from .base snapshot)
  --dry-run           Parse and preview only, no DB writes
  --help              Show this help

Environment (oabp MySQL, same as application.yml):
  DB_HOST, DB_PORT, DB_OABP_NAME (default ${DEFAULT_OABP_DATABASE}), DB_USERNAME, DB_PASSWORD
`);
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {string} recId
 * @returns {Promise<number|null>}
 */
async function findTaskIdByRecId(conn, recId) {
  const prefix = `[feishu:recId=${recId}]%`;
  const [rows] = await conn.query(
    'SELECT id FROM jq_todos_task WHERE remark LIKE ? LIMIT 1',
    [prefix],
  );
  if (!rows.length) return null;
  return Number(rows[0].id);
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {string} recId
 * @param {number} subIndex
 * @returns {Promise<number|null>}
 */
async function findSubtaskIdByRecIdIndex(conn, recId, subIndex) {
  const remark = `[feishu:recId=${recId}:sub=${subIndex}]`;
  const [rows] = await conn.query(
    'SELECT id FROM jq_todos_subtask WHERE remark = ? LIMIT 1',
    [remark],
  );
  if (!rows.length) return null;
  return Number(rows[0].id);
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {string} recId
 * @param {number} keepCount 保留 sub 下标 0..keepCount-1
 * @returns {Promise<number>} 软删条数
 */
async function softDeleteExtraSubtasks(conn, recId, keepCount) {
  const prefix = `[feishu:recId=${recId}:sub=%`;
  const [rows] = await conn.query(
    'SELECT id, remark FROM jq_todos_subtask WHERE remark LIKE ? AND (deleted = 0 OR deleted IS NULL)',
    [prefix],
  );
  let count = 0;
  for (const row of rows) {
    const m = String(row.remark).match(/:sub=(\d+)\]$/);
    if (!m) continue;
    const idx = Number(m[1]);
    if (idx >= keepCount) {
      await conn.query(
        'UPDATE jq_todos_subtask SET deleted = 1, updater = NULL, update_time = NOW() WHERE id = ?',
        [row.id],
      );
      count++;
    }
  }
  return count;
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {ReturnType<typeof rowToTaskPayload>} payload
 * @returns {Promise<number>}
 */
async function insertTask(conn, payload) {
  const [result] = await conn.query(
    `INSERT INTO jq_todos_task (
      task_name, business_block, project_id, decision_maker_user_id,
      progress, start_date, planned_end_date, status, remark, task_detail,
      deleted, tenant_id, creator, create_time, updater, update_time
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW())`,
    [
      payload.task_name,
      payload.business_block,
      payload.project_id,
      payload.decision_maker_user_id,
      payload.progress,
      payload.start_date,
      payload.planned_end_date,
      payload.status,
      payload.remark,
      payload.task_detail,
      payload.deleted,
      payload.tenant_id,
      payload.creator,
      payload.updater,
    ],
  );
  return Number(result.insertId);
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {number} id
 * @param {ReturnType<typeof rowToTaskPayload>} payload
 */
async function updateTask(conn, id, payload) {
  await conn.query(
    `UPDATE jq_todos_task SET
      task_name = ?, business_block = ?, project_id = ?, decision_maker_user_id = ?,
      progress = ?, start_date = ?, planned_end_date = ?, status = ?,
      remark = ?, task_detail = ?, deleted = ?, tenant_id = ?,
      updater = ?, update_time = NOW()
     WHERE id = ?`,
    [
      payload.task_name,
      payload.business_block,
      payload.project_id,
      payload.decision_maker_user_id,
      payload.progress,
      payload.start_date,
      payload.planned_end_date,
      payload.status,
      payload.remark,
      payload.task_detail,
      payload.deleted,
      payload.tenant_id,
      payload.updater,
      id,
    ],
  );
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {ReturnType<typeof rowToSubtaskPayload>} payload
 */
async function insertSubtask(conn, payload) {
  await conn.query(
    `INSERT INTO jq_todos_subtask (
      parent_id, task_name, asignee_id, remark, deleted, tenant_id,
      creator, create_time, updater, update_time
    ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW())`,
    [
      payload.parent_id,
      payload.task_name,
      payload.asignee_id,
      payload.remark,
      payload.deleted,
      payload.tenant_id,
      payload.creator,
      payload.updater,
    ],
  );
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {number} id
 * @param {ReturnType<typeof rowToSubtaskPayload>} payload
 */
async function updateSubtask(conn, id, payload) {
  await conn.query(
    `UPDATE jq_todos_subtask SET
      parent_id = ?, task_name = ?, asignee_id = ?, remark = ?,
      deleted = ?, tenant_id = ?, updater = ?, update_time = NOW()
     WHERE id = ?`,
    [
      payload.parent_id,
      payload.task_name,
      payload.asignee_id,
      payload.remark,
      payload.deleted,
      payload.tenant_id,
      payload.updater,
      id,
    ],
  );
}

/**
 * @param {import('./feishu-base-parser.mjs').NormalizedTaskRow[]} records
 * @param {boolean} dryRun
 */
export async function importRecords(records, dryRun) {
  const stats = {
    taskInserted: 0,
    taskUpdated: 0,
    subtaskInserted: 0,
    subtaskUpdated: 0,
    subtaskSoftDeleted: 0,
    assigneeMiss: [],
  };
  /** @type {import('mysql2/promise').Connection|null} */
  let conn = null;

  if (!dryRun) {
    conn = await connectOabp();
  }

  try {
    for (const row of records) {
      const taskPayload = rowToTaskPayload(row);

      if (dryRun) {
        stats.taskInserted++;
        stats.subtaskInserted += row.assigneeNames.length;
        continue;
      }

      let taskId = await findTaskIdByRecId(conn, row.feishuRecId);
      if (taskId != null) {
        await updateTask(conn, taskId, taskPayload);
        stats.taskUpdated++;
      } else {
        taskId = await insertTask(conn, taskPayload);
        stats.taskInserted++;
      }

      const assigneeCount = row.assigneeNames.length;
      for (let i = 0; i < assigneeCount; i++) {
        const name = row.assigneeNames[i];
        let assigneeId = null;
        if (name) {
          assigneeId = await resolveAssigneeId(conn, name);
          if (assigneeId == null) {
            stats.assigneeMiss.push({ recId: row.feishuRecId, name, task: row.taskName, subIndex: i });
          }
        }
        const subPayload = rowToSubtaskPayload(row, taskId, i, assigneeId);
        const existingSubId = await findSubtaskIdByRecIdIndex(conn, row.feishuRecId, i);
        if (existingSubId != null) {
          await updateSubtask(conn, existingSubId, subPayload);
          stats.subtaskUpdated++;
        } else {
          await insertSubtask(conn, subPayload);
          stats.subtaskInserted++;
        }
      }

      const softDeleted = await softDeleteExtraSubtasks(conn, row.feishuRecId, assigneeCount);
      stats.subtaskSoftDeleted += softDeleted;
    }
  } finally {
    if (conn) await conn.end();
  }

  return stats;
}

async function main() {
  const opts = parseArgs(process.argv);
  const schema = process.env.DB_OABP_NAME || DEFAULT_OABP_DATABASE;
  console.log(`Parsing: ${opts.file}`);
  console.log(`Target schema: ${schema}`);
  const parsed = parseFeishuBase(opts.file);
  if (opts.tenantId) {
    for (const r of parsed.records) {
      r.tenantId = opts.tenantId;
    }
  }

  const totalSubtasks = parsed.records.reduce((n, r) => n + r.assigneeNames.length, 0);
  console.log(`Base: ${parsed.meta.baseName}`);
  console.log(`Tenant: ${parsed.tenantId}${opts.tenantId ? ' (CLI override)' : ''}`);
  console.log(`Records: ${parsed.records.length} (raw recordMap: ${parsed.meta.rawRecordCount})`);
  console.log(`Expected subtasks (assignees): ${totalSubtasks}`);

  if (opts.dryRun) {
    console.log('\n--- dry-run preview (first 2) ---');
    for (const r of parsed.records.slice(0, 2)) {
      console.log(JSON.stringify({
        recId: r.feishuRecId,
        taskName: r.taskName,
        assignees: r.assigneeNames,
        longTerm: r.longTerm || null,
        taskPayload: rowToTaskPayload(r),
        subtaskCount: r.assigneeNames.length,
      }, null, 2));
    }
  }

  const stats = await importRecords(parsed.records, opts.dryRun);

  console.log('\n--- result ---');
  if (opts.dryRun) {
    console.log(`Would upsert tasks: ${stats.taskInserted}`);
    console.log(`Would upsert subtasks: ${stats.subtaskInserted}`);
  } else {
    console.log(`Tasks inserted: ${stats.taskInserted}, updated: ${stats.taskUpdated}`);
    console.log(`Subtasks inserted: ${stats.subtaskInserted}, updated: ${stats.subtaskUpdated}`);
    if (stats.subtaskSoftDeleted > 0) {
      console.log(`Subtasks soft-deleted (stale): ${stats.subtaskSoftDeleted}`);
    }
  }
  const realMiss = stats.assigneeMiss;
  if (realMiss.length) {
    console.log(`Assignee miss (${realMiss.length}):`);
    for (const m of realMiss.slice(0, 15)) {
      console.log(`  - ${m.name} (${m.recId}) ${m.task.slice(0, 40)}`);
    }
    if (realMiss.length > 15) {
      console.log(`  ... and ${realMiss.length - 15} more`);
    }
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
