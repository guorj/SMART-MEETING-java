#!/usr/bin/env node
/**
 * 仅下载飞书妙记（元数据 + 音视频 + 飞书原生转录）。
 * 全文离线转写请用: npm run vc-minute-offline-transcribe
 */
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { downloadFeishuMinute, extractMinuteToken } from './lib/feishu-minutes.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..');

const minuteToken = extractMinuteToken(process.argv[2]);
if (!minuteToken) {
  console.error('usage: node scripts/fetch-feishu-minute.mjs <minute_url_or_token> [out_dir]');
  process.exit(1);
}
const outDir = path.resolve(REPO_ROOT, process.argv[3] || path.join('data', 'minutes', minuteToken));

const result = await downloadFeishuMinute({ minuteToken, outDir, includeFeishuTranscript: true });
console.log(`title=${result.title || '(unknown)'} duration_ms=${result.durationMs}`);
if (result.audioPath) console.log(`audio: ${result.audioPath}`);
if (result.feishuTranscriptPath) console.log(`transcript: ${result.feishuTranscriptPath}`);
console.log(`done -> ${outDir}`);
