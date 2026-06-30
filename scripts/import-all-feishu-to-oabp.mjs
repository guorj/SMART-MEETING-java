#!/usr/bin/env node
/**
 * 一次性导入飞书 .base 到 oabp（默认 oabp_pro）：jq_todos_* + jq_project_task_tracking
 */
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DEFAULT_OABP_DATABASE } from './oabp-db.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const db = process.env.DB_OABP_NAME || DEFAULT_OABP_DATABASE;
const extraArgs = process.argv.slice(2);

console.log(`Import target schema: ${db}`);

/**
 * @param {string} script
 * @returns {Promise<void>}
 */
function run(script) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(__dirname, script), ...extraArgs], {
      stdio: 'inherit',
      env: { ...process.env, DB_OABP_NAME: db },
    });
    child.on('exit', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`${script} exited with ${code}`));
    });
  });
}

await run('import-feishu-base-to-jq-todos.mjs');
await run('import-feishu-base-to-oabp.mjs');
console.log('\nAll imports completed.');
