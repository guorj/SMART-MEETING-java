import mysql from 'mysql2/promise';

/** 与 meeting-server application.yml 默认一致 */
export const DEFAULT_OABP_DATABASE = 'oabp_pro';

/** 用户目录仍在 oabp 库（oabp_pro 可能无 system_users 表） */
export const SYSTEM_USERS_DATABASE = process.env.DB_OABP_USERS_SCHEMA || 'oabp';

/**
 * @returns {import('mysql2/promise').ConnectionOptions}
 */
export function oabpConnectionOptions() {
  return {
    host: process.env.DB_HOST || '60.205.1.17',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_OABP_USERNAME || process.env.DB_USERNAME || 'oabp',
    password: process.env.DB_OABP_PASSWORD || process.env.DB_PASSWORD || 'oabp@2026',
    database: process.env.DB_OABP_NAME || DEFAULT_OABP_DATABASE,
    timezone: '+08:00',
  };
}

/**
 * @returns {Promise<import('mysql2/promise').Connection>}
 */
export async function connectOabp() {
  const opts = oabpConnectionOptions();
  try {
    return await mysql.createConnection(opts);
  } catch (e) {
    const msg = e?.message || String(e);
    if (msg.includes('oabp_pro') || msg.includes('Access denied')) {
      throw new Error(
        `${msg}\n\n若尚未创建 oabp_pro，请 DBA 执行 scripts/sql/bootstrap-oabp-pro.sql 后再导入。`,
      );
    }
    throw e;
  }
}

/**
 * @param {import('mysql2/promise').Connection} conn
 * @param {string} nickname
 * @returns {Promise<number|null>}
 */
export async function resolveAssigneeId(conn, nickname) {
  if (!nickname) return null;
  const usersSchema = SYSTEM_USERS_DATABASE;
  const [rows] = await conn.query(
    `SELECT id FROM \`${usersSchema}\`.system_users WHERE nickname = ? AND (deleted = 0 OR deleted IS NULL) LIMIT 1`,
    [nickname],
  );
  if (!rows.length) return null;
  return Number(rows[0].id);
}
