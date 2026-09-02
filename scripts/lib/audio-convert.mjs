import { spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * 容器音频 → 16k mono s16le PCM（与 meeting-server FfmpegAudioConverter 一致）。
 * @param {string} inputPath
 * @param {string} [outputPath]
 * @returns {Promise<string>} PCM 路径
 */
export async function convertToPcm16k(inputPath, outputPath) {
  const pcmPath = outputPath || inputPath.replace(/\.[^.]+$/, '.pcm');
  await fs.mkdir(path.dirname(pcmPath), { recursive: true });

  const result = spawnSync(
    'ffmpeg',
    ['-y', '-i', inputPath, '-f', 's16le', '-acodec', 'pcm_s16le', '-ar', '16000', '-ac', '1', pcmPath],
    { encoding: 'utf8' },
  );
  if (result.status !== 0) {
    throw new Error(`ffmpeg failed: ${result.stderr || result.stdout || result.status}`);
  }
  const stat = await fs.stat(pcmPath);
  if (stat.size <= 0) throw new Error(`ffmpeg produced empty pcm: ${pcmPath}`);
  return pcmPath;
}
