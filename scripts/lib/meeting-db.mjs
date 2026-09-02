import mysql from 'mysql2/promise';

export const MINUTES_REFRESH_CONFIG_KEY = 'meeting.feishu.minutes.user-refresh-token';

/**
 * meeting-server 使用的 intelligence 库连接（读/写系统参数）。
 * @returns {import('mysql2/promise').ConnectionOptions}
 */
export function intelligenceConnectionOptions() {
  return {
    host: process.env.MYSQL_HOST || process.env.DB_HOST || '60.205.1.17',
    port: Number(process.env.MYSQL_PORT || process.env.DB_PORT || 3306),
    user: process.env.MYSQL_USER || process.env.DB_USERNAME || 'intelligence',
    password: process.env.MYSQL_PASSWORD || process.env.DB_PASSWORD || 'intelligence@2026',
    database: process.env.MYSQL_DB || process.env.DB_NAME || 'intelligence',
    timezone: '+08:00',
  };
}

/**
 * @returns {Promise<import('mysql2/promise').Connection>}
 */
export async function connectIntelligence() {
  return mysql.createConnection(intelligenceConnectionOptions());
}

/**
 * 从 int_meeting_system_config 读取妙记 user refresh_token。
 * @returns {Promise<string>}
 */
export async function loadMinutesRefreshToken() {
  const conn = await connectIntelligence();
  try {
    const [rows] = await conn.execute(
      'SELECT value_json FROM int_meeting_system_config WHERE config_key = ? LIMIT 1',
      [MINUTES_REFRESH_CONFIG_KEY],
    );
    if (!rows.length) {
      throw new Error(`missing config ${MINUTES_REFRESH_CONFIG_KEY}; complete Feishu OAuth in Admin first`);
    }
    let raw = rows[0].value_json;
    if (typeof raw !== 'string') raw = JSON.stringify(raw);
    if (raw.startsWith('"')) return JSON.parse(raw);
    return raw;
  } finally {
    await conn.end();
  }
}

/**
 * 飞书 OAuth 轮换 refresh_token 时写回 DB（与 FeishuMinutesRefreshTokenPersister 一致）。
 * @param {string} newRefresh
 */
export async function persistMinutesRefreshToken(newRefresh) {
  if (!newRefresh) return;
  const conn = await connectIntelligence();
  try {
    await conn.execute(
      'UPDATE int_meeting_system_config SET value_json = ?, updated_at = NOW() WHERE config_key = ?',
      [JSON.stringify(newRefresh), MINUTES_REFRESH_CONFIG_KEY],
    );
  } finally {
    await conn.end();
  }
}
