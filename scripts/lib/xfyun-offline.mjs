import crypto from 'node:crypto';

const APP_ID = process.env.XFYUN_APP_ID || process.env.MEETING_ASR_XFYUN_APP_ID || 'e88682b8';
const ACCESS_KEY_ID = process.env.XFYUN_ACCESS_KEY_ID || process.env.MEETING_ASR_XFYUN_API_KEY || 'cf2d80e0d6bddb829c43d147dfc1d664';
const ACCESS_KEY_SECRET = process.env.XFYUN_ACCESS_KEY_SECRET || process.env.MEETING_ASR_XFYUN_API_SECRET || 'NGFmOGU1MDRjNGQ3NTQwY2NkOTZmNTBl';
const BASE = process.env.XFYUN_IST_BASE_URL || 'https://office-api-ist-dx.iflyaisol.com';

export function formatIstDateTime(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0');
  const off = -date.getTimezoneOffset();
  const sign = off >= 0 ? '+' : '-';
  const oh = pad(Math.floor(Math.abs(off) / 60));
  const om = pad(Math.abs(off) % 60);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}${sign}${oh}${om}`;
}

export function randomSignatureNonce() {
  return crypto.randomBytes(8).toString('hex');
}

/**
 * 与 {@link com.smartmeeting.asr.XfyunOfflineClient#generateSignature} 一致。
 * @param {Record<string, string>} params
 * @param {string} secret
 */
export function signIstParams(params, secret) {
  const sorted = Object.keys(params).sort();
  const body = sorted
    .filter((k) => k !== 'signature' && params[k] != null && params[k] !== '')
    .map((k) => `${k}=${encodeURIComponent(params[k])}`)
    .join('&');
  return crypto.createHmac('sha1', secret).update(body).digest('base64');
}

function encodeQuery(params) {
  return Object.entries(params)
    .map(([k, v]) => `${k}=${encodeURIComponent(v).replace(/\+/g, '%20')}`)
    .join('&');
}

/**
 * @typedef {object} XfyunUploadResult
 * @property {string} orderId
 * @property {string} signatureRandom
 * @property {number} taskEstimateTimeMs
 */

/**
 * 上传 PCM/音频至讯飞 IST v2。
 * durationCheckDisable=true：避免 PCM 字节估算 duration 与讯飞解析偏差导致 100020。
 * @param {Buffer} audio
 * @param {string} fileName
 * @param {object} [opts]
 * @param {number} [opts.roleType=1]
 * @param {number} [opts.roleNum=0]
 * @returns {Promise<XfyunUploadResult>}
 */
export async function uploadIstAudio(audio, fileName, opts = {}) {
  const dateTime = formatIstDateTime();
  const signatureRandom = randomSignatureNonce();
  const params = {
    appId: APP_ID,
    accessKeyId: ACCESS_KEY_ID,
    dateTime,
    signatureRandom,
    fileSize: String(audio.length),
    fileName,
    language: 'autodialect',
    durationCheckDisable: 'true',
    roleType: String(opts.roleType ?? 1),
    roleNum: String(opts.roleNum ?? 0),
  };
  const signature = signIstParams(params, ACCESS_KEY_SECRET);
  const url = `${BASE}/v2/upload?${encodeQuery(params)}`;
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream', signature },
    body: audio,
  });
  const json = await resp.json();
  if (json.code !== '000000') {
    throw new Error(`Xfyun upload failed: ${json.code} ${json.descInfo || ''}`);
  }
  return {
    orderId: json.content.orderId,
    signatureRandom,
    taskEstimateTimeMs: Number(json.content.taskEstimateTime || 0),
  };
}

/**
 * @param {string} orderId
 * @param {string} uploadSignatureRandom upload 时的 signatureRandom（poll 须复用）
 * @param {object} [opts]
 * @param {number} [opts.maxRetries=360]
 * @param {number} [opts.intervalMs=5000]
 * @param {(info: { attempt: number, status: number }) => void} [opts.onPoll]
 * @returns {Promise<string>} orderResult JSON 字符串
 */
export async function pollIstResult(orderId, uploadSignatureRandom, opts = {}) {
  const maxRetries = opts.maxRetries ?? 360;
  const intervalMs = opts.intervalMs ?? 5000;

  for (let i = 0; i < maxRetries; i++) {
    const dateTime = formatIstDateTime();
    const params = {
      accessKeyId: ACCESS_KEY_ID,
      dateTime,
      signatureRandom: uploadSignatureRandom,
      orderId,
      resultType: 'transfer',
    };
    const signature = signIstParams(params, ACCESS_KEY_SECRET);
    const url = `${BASE}/v2/getResult?${encodeQuery(params)}`;
    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', signature },
      body: '{}',
    });
    const json = await resp.json();
    if (json.code !== '000000') {
      throw new Error(`Xfyun poll failed: ${json.code} ${json.descInfo || ''}`);
    }
    const status = json.content?.orderInfo?.status;
    opts.onPoll?.({ attempt: i + 1, status });
    if (status === 4) {
      const orderResult = json.content.orderResult;
      if (!orderResult) throw new Error('Xfyun order complete but orderResult empty');
      return orderResult;
    }
    if (status === -1) throw new Error('Xfyun order failed');
    const wait = i < 5 ? intervalMs : Math.max(intervalMs, 10000);
    await new Promise((r) => setTimeout(r, wait));
  }
  throw new Error('Xfyun poll timeout');
}

/**
 * 解析 IST lattice → 分段（与 OfflineAsrLatticeParser 字段对齐）。
 * @param {string} orderResult
 * @returns {Array<{ startMs: number, endMs: number, speakerId: string, text: string }>}
 */
export function parseIstSegments(orderResult) {
  const root = JSON.parse(orderResult);
  const segments = [];
  for (const lattice of root.lattice || []) {
    const best = JSON.parse(lattice.json_1best || '{}');
    const st = best.st || {};
    const text = (st.rt || [])
      .flatMap((r) => (r.ws || []).map((w) => (w.cw || []).map((c) => c.w).join('')))
      .join('');
    if (!text) continue;
    const role = st.rl ?? '0';
    segments.push({
      startMs: Number(st.bg || 0),
      endMs: Number(st.ed || st.bg || 0),
      speakerId: `speaker_${role}`,
      text,
    });
  }
  return segments.sort((a, b) => a.startMs - b.startMs);
}

function formatMs(ms) {
  const s = Math.floor(ms / 1000);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/**
 * @param {Array<{ startMs: number, endMs: number, speakerId: string, text: string }>} segments
 * @param {object} [meta]
 */
export function formatTranscriptText(segments, meta = {}) {
  let out = '# 讯飞离线全文转写\n';
  if (meta.minuteToken) out += `minuteToken: ${meta.minuteToken}\n`;
  if (meta.title) out += `title: ${meta.title}\n`;
  out += `segments: ${segments.length}\n\n`;
  for (const s of segments) {
    out += `[${formatMs(s.startMs)} - ${formatMs(s.endMs)}] ${s.speakerId}: ${s.text.trim()}\n`;
  }
  return out;
}

/**
 * 上传 → 轮询 → 解析。
 * @param {Buffer} audio
 * @param {string} fileName
 * @param {object} [opts]
 */
export async function transcribeIstAudio(audio, fileName, opts = {}) {
  const upload = await uploadIstAudio(audio, fileName, opts);
  opts.onUpload?.(upload);
  const orderResult = await pollIstResult(upload.orderId, upload.signatureRandom, opts);
  const segments = parseIstSegments(orderResult);
  return { upload, segments, orderResult };
}
