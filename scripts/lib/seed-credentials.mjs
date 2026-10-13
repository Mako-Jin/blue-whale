/**
 * 种子账号的凭据 —— **不要在脚本里再抄一份口令**。
 *
 * ## 为什么需要它
 *
 * `scripts/README.md` 的硬性约束写着「**凭据走环境变量**：脚本里不出现任何密码
 * （见 `docs/decisions/0003`）」，而 4 个 `verify-*.mjs` 曾经**各自硬编码了同一对口令**。
 * 那不是"多写了几行"，而是把一个**会变的值**复制了 4 份：
 * 口令一旦在 `application-h2.yml` 里改掉，四个脚本会一起静默失效
 * （症状是"e2e 登录失败"，而不是"某处配置不一致"）。
 *
 * 现在口令的真值来源只有一处：`application-h2.yml` 的 `seed-admin.password` /
 * `seed-auditor.password`（理由见 `docs/decisions/0029`）。本模块负责读出来。
 *
 * ## 为什么不引 YAML 库
 *
 * 只取两个键，而这份文件的形状由本仓自己控制；引一个 YAML 依赖为两个键不值得。
 * 代价是解析器很朴素 —— 所以它**读到空值就抛错**，绝不安静地返回 undefined
 * （那样脚本会拿着空口令去登录，报一个与真正原因相隔很远的错）。
 *
 * ## 覆盖
 *
 * 环境变量优先，用于把脚本指向另一套账号（例如 MySQL 库里自己种的账号）：
 *
 *     BW_SEED_ADMIN_PASSWORD=… BW_SEED_AUDITOR_PASSWORD=… node scripts/verify-admin-permissions.mjs
 */
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const H2_PROFILE = join(
  ROOT,
  'server',
  'sts-app',
  'sts-app-server',
  'src',
  'main',
  'resources',
  'application-h2.yml',
);

/** 去掉 YAML 里可能存在的单/双引号 */
function unquote(value) {
  const matched = /^(['"])(.*)\1$/.exec(value);
  return matched ? matched[2] : value;
}

/**
 * 从 profile 文本里取出 `seed-admin` / `seed-auditor` 两个块下的 `password`。
 *
 * 逐行的朴素解析：块由"缩进更深的 `password:`"构成，遇到缩进回退到同级或更浅就结束。
 */
function parsePasswords(text) {
  const found = {};
  let block = null;
  let blockIndent = -1;

  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;

    const indent = raw.length - raw.trimStart().length;
    const heading = /^(seed-admin|seed-auditor):\s*$/.exec(line);
    if (heading) {
      block = heading[1];
      blockIndent = indent;
      continue;
    }
    if (block && indent <= blockIndent) {
      block = null;
      continue;
    }
    if (block) {
      const password = /^password:\s*(.*)$/.exec(line);
      if (password) found[block] = unquote(password[1].trim());
    }
  }
  return found;
}

function resolvePassword(envName, parsed, block) {
  const fromEnv = process.env[envName];
  if (fromEnv) return fromEnv;

  const value = parsed[block];
  if (!value) {
    throw new Error(
      `${H2_PROFILE} 里读不到 ${block}.password。\n` +
        '  种子口令没有默认值了（见 docs/decisions/0029）—— 请在该 profile 里显式提供，\n' +
        `  或用环境变量覆盖：${envName}=… node <脚本>`,
    );
  }
  return value;
}

const parsed = parsePasswords(readFileSync(H2_PROFILE, 'utf8'));

/** 超级管理员 —— 全部权限 */
export const ADMIN = {
  tenantCode: 'default',
  username: 'admin',
  password: resolvePassword('BW_SEED_ADMIN_PASSWORD', parsed, 'seed-admin'),
};

/** 只读审计员 —— 用来证明"写操作确实被拦住了" */
export const AUDITOR = {
  tenantCode: 'default',
  username: 'auditor',
  password: resolvePassword('BW_SEED_AUDITOR_PASSWORD', parsed, 'seed-auditor'),
};
