import fs from 'node:fs/promises';
import path from 'node:path';
import { loadMinutesRefreshToken, persistMinutesRefreshToken } from './meeting-db.mjs';

const FEISHU_BASE = process.env.FEISHU_BASE_URL || 'https://open.feishu.cn';
const APP_ID = process.env.FEISHU_APP_ID || 'cli_a9742c7795795bc2';
const APP_SECRET = process.env.FEISHU_APP_SECRET || 'yNKh96SePvhk4h3b1Z1Rnjw3RZ6fivIj';

/**
 * 从妙记 URL 或裸 token 提取 minute_token。
 * @param {string} input
 * @returns {string}
 */
export function extractMinuteToken(input) {
  const m = String(input || '').match(/\/minutes\/([A-Za-z0-9]+)/);
  return m ? m[1] : String(input || '').trim();
}

/**
 * @returns {Promise<string>} user_access_token
 */
export async function getUserAccessToken() {
  const refreshToken = await loadMinutesRefreshToken();
  const resp = await fetch(`${FEISHU_BASE}/open-apis/authen/v2/oauth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({
      grant_type: 'refresh_token',
      client_id: APP_ID,
      client_secret: APP_SECRET,
      refresh_token: refreshToken,
    }),
  });
  const data = await resp.json();
  if (data.code !== 0) {
    throw new Error(`Feishu token refresh failed: ${data.error_description || data.msg || data.error}`);
  }
  if (data.refresh_token && data.refresh_token !== refreshToken) {
    await persistMinutesRefreshToken(data.refresh_token);
  }
  return data.access_token;
}

async function feishuGet(url, accessToken) {
  const resp = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
  const ct = resp.headers.get('content-type') || '';
  if (ct.includes('application/json')) {
    const json = await resp.json();
    if (json.code !== undefined && json.code !== 0) {
      throw new Error(`Feishu API ${url}: code=${json.code} msg=${json.msg}`);
    }
    return { kind: 'json', data: json };
  }
  const buf = Buffer.from(await resp.arrayBuffer());
  return { kind: 'binary', data: buf };
}

async function downloadFile(url, dest) {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`download failed ${resp.status} ${url}`);
  const buf = Buffer.from(await resp.arrayBuffer());
  await fs.writeFile(dest, buf);
  return buf.length;
}

/**
 * 下载妙记元数据、音视频、飞书原生转录（File C）。
 * @param {object} opts
 * @param {string} opts.minuteToken
 * @param {string} opts.outDir
 * @param {boolean} [opts.includeFeishuTranscript=true]
 * @returns {Promise<{ title: string, durationMs: number, audioPath: string|null, feishuTranscriptPath: string|null }>}
 */
export async function downloadFeishuMinute({ minuteToken, outDir, includeFeishuTranscript = true }) {
  await fs.mkdir(outDir, { recursive: true });
  const accessToken = await getUserAccessToken();

  const meta = await feishuGet(`${FEISHU_BASE}/open-apis/minutes/v1/minutes/${minuteToken}`, accessToken);
  await fs.writeFile(path.join(outDir, 'meta.json'), JSON.stringify(meta.data, null, 2), 'utf8');
  const minute = meta.data?.data?.minute || meta.data?.minute || {};

  let audioPath = null;
  const media = await feishuGet(`${FEISHU_BASE}/open-apis/minutes/v1/minutes/${minuteToken}/media`, accessToken);
  const downloadUrl = media.data?.data?.download_url;
  if (downloadUrl) {
    const extMatch = downloadUrl.match(/\.(mp4|m4a|mp3|wav)(\?|$)/i);
    const ext = extMatch ? extMatch[1].toLowerCase() : 'm4a';
    audioPath = path.join(outDir, `audio.${ext}`);
    await downloadFile(downloadUrl, audioPath);
  }

  let feishuTranscriptPath = null;
  if (includeFeishuTranscript) {
    const transcriptUrl =
      `${FEISHU_BASE}/open-apis/minutes/v1/minutes/${minuteToken}/transcript` +
      '?need_speaker=true&need_timestamp=true&file_format=txt';
    const transcript = await feishuGet(transcriptUrl, accessToken);
    feishuTranscriptPath = path.join(outDir, 'transcript.txt');
    if (transcript.kind === 'binary') {
      await fs.writeFile(feishuTranscriptPath, transcript.data);
    } else {
      await fs.writeFile(feishuTranscriptPath, JSON.stringify(transcript.data, null, 2), 'utf8');
    }
  }

  return {
    title: minute.title || '',
    durationMs: Number(minute.duration || 0),
    audioPath,
    feishuTranscriptPath,
  };
}
