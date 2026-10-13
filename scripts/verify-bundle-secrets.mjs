#!/usr/bin/env node
/**
 * 前端产物里**不得**出现种子口令 —— 把 `docs/decisions/0029` 的一条约定变成可重复执行的检查。
 *
 * ## 为什么需要它
 *
 * `0029` 的结论是"口令不许有默认值"，随之而来的要求是：
 * **任何"把开发凭据写出来"的地方都要 `import.meta.env.DEV` 门控**，
 * 且**验证方式是构建后 grep 产物，不是读代码判断**。
 *
 * 这条要求此前只被手工执行过一次（当时的 grep 结果写在 `0029` 里）。
 * 手工执行的检查会**随下一次改动失效而无人知道** —— 门控写错、调用点被复制、
 * 新的子应用又抄了一份口令，都不会有任何信号。所以把它固化成脚本。
 *
 * ## 为什么不写口令字面量
 *
 * 口令从 `scripts/lib/seed-credentials.mjs` 取（其真值来源是 `application-h2.yml`）。
 * 于是本脚本里**没有口令**，口令一旦被改，检查跟着改，不会静默失效。
 *
 * ## 它**不是**密钥扫描器
 *
 * 只查"已知的种子口令有没有被卷进前端产物"这一件事。令牌泄露、源码里的
 * 第三方 key 之类都不在射程内 —— 那些需要另一类工具（本仓也没有引入）。
 *
 * ## 用法
 *
 *     node scripts/verify-bundle-secrets.mjs              # 扫 web/platform 下各应用的 dist
 *     node scripts/verify-bundle-secrets.mjs <dir> [...]  # 扫指定目录（可多个）
 *
 * 退出码：发现命中为 `1`，全部干净为 `0`，因此可以直接放进流水线。
 *
 * **只输出计数，不输出命中的口令本身** —— 否则这个检查自己就把口令打进了日志。
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { ADMIN, AUDITOR } from './lib/seed-credentials.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PLATFORM_DIR = join(ROOT, 'web', 'platform');

/** 会被当成文本读的产物扩展名。二进制（图片/字体）不参与 —— 它们不会装下口令 */
const TEXT_EXTENSIONS = new Set(['.js', '.mjs', '.cjs', '.css', '.html', '.htm', '.json', '.map', '.txt', '.svg']);

/** 待检的口令。**标签里也不写口令本身** */
const NEEDLES = [
  { label: '种子管理员口令', value: ADMIN.password },
  { label: '种子审计员口令', value: AUDITOR.password },
];

/** 默认范围：每个子应用的构建产物目录 */
function defaultTargets() {
  if (!existsSync(PLATFORM_DIR)) {
    return [];
  }
  return readdirSync(PLATFORM_DIR)
    .map((name) => join(PLATFORM_DIR, name, 'dist'))
    .filter((dir) => existsSync(dir))
    .sort();
}

/** 递归收集目录下的文本文件 */
function collectFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      files.push(...collectFiles(path));
    } else if (TEXT_EXTENSIONS.has(extname(entry).toLowerCase())) {
      files.push(path);
    }
  }
  return files;
}

const targets = process.argv.slice(2);
const dirs = targets.length > 0 ? targets.map((dir) => resolve(dir)) : defaultTargets();

if (dirs.length === 0) {
  console.error('没有可检查的产物目录。先构建前端，或显式传入目录：');
  console.error('  node scripts/verify-bundle-secrets.mjs web/platform/platform-portal/dist');
  process.exit(1);
}

let hitTotal = 0;
let scannedTotal = 0;

console.log(`检查 ${NEEDLES.length} 个种子口令是否出现在 ${dirs.length} 个产物目录里\n`);

for (const dir of dirs) {
  const label = relative(ROOT, dir).split(sep).join('/') || dir;
  if (!existsSync(dir)) {
    console.log(`  ${label.padEnd(46)} 跳过（未构建）`);
    continue;
  }

  const files = collectFiles(dir);
  scannedTotal += files.length;

  /** 每个口令命中的文件数；不记录文件名（文件名无害，但少一份可被拼起来的线索） */
  const hits = NEEDLES.map(() => 0);

  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    NEEDLES.forEach((needle, index) => {
      if (text.includes(needle.value)) {
        hits[index] += 1;
      }
    });
  }

  const sum = hits.reduce((total, count) => total + count, 0);
  hitTotal += sum;

  if (sum === 0) {
    console.log(`  ${label.padEnd(46)} 干净（${files.length} 个文件）`);
  } else {
    const detail = NEEDLES.map((needle, index) => (hits[index] > 0 ? `${needle.label} ×${hits[index]} 文件` : null))
      .filter(Boolean)
      .join(' · ');
    console.log(`  ${label.padEnd(46)} **命中**：${detail}`);
  }
}

console.log(`\n共扫描 ${scannedTotal} 个文件。`);

if (hitTotal > 0) {
  console.error(
    '\n失败：种子口令出现在了前端产物里。\n' +
      '  开发凭据只能在 `import.meta.env.DEV` 为真的分支里构造（见 docs/decisions/0029 · 0033），\n' +
      '  且该分支要能在生产构建里被静态摇掉 —— 例如：\n' +
      '    devAccount: import.meta.env.DEV ? { ... } : undefined',
  );
  process.exit(1);
}

console.log('全部干净：种子口令没有进入任何前端产物。');
