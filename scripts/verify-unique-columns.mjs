#!/usr/bin/env node
/**
 * 判重与唯一索引对账 —— 服务层挡重名的每一列，要么库上有 UNIQUE 索引兜底，要么明写着"有意无索引"。
 *
 * ## 为什么需要它
 *
 * `docs/decisions/0062` 立了判据：**判一个列该不该挡重名，看它语义上是否指认唯一对象，
 * 不是看它有没有索引**。但判据本身没有东西守 —— `TENANT_NAME_EXISTS` 就是那样长出来的：
 * 一道"先查后写"的判重加上没有唯一索引，"以为在保证唯一、实际保证不了"，还拦掉了
 * 合法情形。它活了多久没有人发现，就说明这件事**靠人回溯源码是守不住的**。
 *
 * 本脚本把 `0062` 那轮的手工对账机械化。它守的是一条**语法级**事实的对账：
 * "哪些列在服务层被挡重名"（代码里的 `*_EXISTS` 抛出）×"哪些列在库上有 UNIQUE 索引"
 * （三方言迁移脚本）。四格里，三格是对的形态：
 *
 * |               | 库上有 UNIQUE            | 库上没有                 |
 * | ------------- | ------------------------ | ------------------------ |
 * | 服务层判重    | ✓ 数据约束级（双保险）   | 白名单（业务规则级唯一） |
 * | 服务层不判重  | ✓ 数据约束级兜底，合法   | ✓ 名称列，`0062` 的结论  |
 *
 * 只有"服务层判重 + 库上无索引 + 不在白名单"这一格要红。
 *
 * ## 它查什么
 *
 * 1. **每个被引用的 `*_EXISTS` 码都登记了它判的是哪一列**（`JUDGED`）。登记这个动作
 *    本身就是 `0062` 判据的落点 —— 新加判重的人必须先回答"这个列指认什么"。
 * 2. **三方言的唯一索引集合一致**。三份迁移脚本本该是同一次提交的拷贝（`0038`），
 *    此前谁改一份漏两份没有任何东西会发现。
 * 3. **判重列要么有 UNIQUE、要么在 `NO_INDEX_ALLOWED` 里写明理由**。
 *    白名单反向腐烂（有人给 email 加了 UNIQUE，理由过期）同样红。
 *
 * ## 用法
 *
 *     node scripts/verify-unique-columns.mjs
 *
 * 不需要任何前置条件（不连网、不编译、不读产物），失败时非零退出。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **"有 UNIQUE 却不判重"**（如 `bw_auth_refresh_token.jti`）：合法形态 —— 数据约束
 *   自己会以 `40900` 兜底，服务层不用提前查。所以表格右上那一格不查。
 * - **判重的实现方式**。本脚本锚定的是 `*_EXISTS` 码的**引用**，不解析"先查后写"的
 *   具体写法（`selectCount` / `selectAny*` / exists helper 都行）—— 那是代码风格，
 *   不是本守卫要钉的事实。
 * - **并发窗口本身**。`email`/`phone` 在白名单里就是**有意**接受并发漏网的
 *   （`docs/decisions/0047`）；守卫不替产品决定这件事，只保证它**始终是显式决定**。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SERVER = join(ROOT, 'server');

/**
 * 服务层挡重名的列 —— 键是被引用的 `*_EXISTS` 码全名，值是它判的那一列。
 *
 * 新加一个判重必须先来这里登记：登记的动作就是回答 `0062` 的问题
 * ——"这个列在语义上指认什么？"答不出"唯一对象"的，别写判重。
 */
const JUDGED = new Map([
  ['AuthResultCode.TENANT_CODE_EXISTS', { table: 'bw_auth_tenant', column: 'tenant_code' }],
  ['AuthResultCode.USERNAME_EXISTS', { table: 'bw_auth_user', column: 'username' }],
  ['AuthResultCode.USER_EMAIL_EXISTS', { table: 'bw_auth_user', column: 'email' }],
  ['AuthResultCode.USER_PHONE_EXISTS', { table: 'bw_auth_user', column: 'phone' }],
  ['AuthResultCode.ORG_CODE_EXISTS', { table: 'bw_auth_organization', column: 'org_code' }],
  ['AuthResultCode.ROLE_CODE_EXISTS', { table: 'bw_auth_role', column: 'role_code' }],
  ['AuthResultCode.USER_GROUP_CODE_EXISTS', { table: 'bw_auth_user_group', column: 'group_code' }],
  ['AuthResultCode.RESOURCE_CODE_EXISTS', { table: 'bw_auth_resource', column: 'resource_code' }],
]);

/**
 * 允许"无唯一索引却判重"的列 —— 每一条都必须写明理由。
 *
 * 这就是 `0062` 三分里的"寻址"类：指认现实世界的主体，重名让**人**做错事
 * （找不到该联系谁），所以服务层挡；但不加 UNIQUE，因为那会把"误删后换人
 * 绑定同一邮箱"永久锁住（理由在 UserService 的注释与 `0047`）——
 * 并发下的漏网是**有意接受**的权衡，不是半吊子实现。
 */
const NO_INDEX_ALLOWED = new Map([
  [
    'bw_auth_user.email',
    {
      reason:
        '业务规则级唯一（docs/decisions/0062，依据 0047）：邮箱指认现实世界的主体，服务层判重' +
        '（建号与改号两处，见 UserService#existsEmail），但**有意**不加 UNIQUE —— 加了会让一次误删' +
        '把邮箱永久锁住（软删行占名）。并发下两个同邮箱请求都能穿过"先查后写"，这是接受的权衡',
    },
  ],
  [
    'bw_auth_user.phone',
    {
      reason: '同上（docs/decisions/0062）：手机号与邮箱同属"寻址"类，处置一致（UserService#existsPhone）',
    },
  ],
]);

const problems = [];

function check(label, ok, detail) {
  console.log(`${ok ? '✓' : '✗'} ${label}`);
  if (detail) console.log(`    ${detail}`);
  if (!ok) problems.push(`${label}${detail ? ` —— ${detail}` : ''}`);
}

/* ------------------------------------------------------------------ 扫源码 */

function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/[^\n]*/g, '$1 ');
}

function collectMainSources() {
  const roots = [];
  if (!existsSync(SERVER)) return roots;
  for (const group of readdirSync(SERVER, { withFileTypes: true })) {
    if (!group.isDirectory()) continue;
    const groupPath = join(SERVER, group.name);
    for (const module of readdirSync(groupPath, { withFileTypes: true })) {
      if (!module.isDirectory()) continue;
      const mainJava = join(groupPath, module.name, 'src', 'main', 'java');
      if (existsSync(mainJava)) roots.push(mainJava);
    }
  }

  const files = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.java')) files.push(full);
    }
  };
  roots.forEach(walk);
  return files;
}

// 一次遍历收集所有 `*_EXISTS` 码的引用（剥注释：javadoc 里的说明不是判重）
const referenced = new Set();
const sources = collectMainSources();
for (const file of sources) {
  const code = stripComments(readFileSync(file, 'utf8'));
  for (const matched of code.matchAll(/\b(ResultCode|AuthResultCode)\.([A-Z][A-Z0-9_]*_EXISTS)\b/g)) {
    referenced.add(`${matched[1]}.${matched[2]}`);
  }
}

/* ------------------------------------------------------------ 解析三方言 DDL */

const DIALECTS = ['h2', 'mysql', 'dameng'];
const UNIQUE_RE = /CREATE\s+UNIQUE\s+INDEX\s+\S+\s+ON\s+(\w+)\s*\(([^)]+)\)/gi;

/** dialect → Map<'table.column', 文件名> */
const uniqueByDialect = new Map();
for (const dialect of DIALECTS) {
  const dir = join(SERVER, 'sts-server', 'sts-server-auth', 'src', 'main', 'resources', 'db', 'migration', dialect);
  const map = new Map();
  if (existsSync(dir)) {
    const walk = (d) => {
      for (const entry of readdirSync(d, { withFileTypes: true })) {
        const full = join(d, entry.name);
        if (entry.isDirectory()) walk(full);
        else if (entry.name.endsWith('.sql')) {
          const text = readFileSync(full, 'utf8');
          for (const matched of text.matchAll(UNIQUE_RE)) {
            const table = matched[1];
            for (const column of matched[2].split(',')) {
              const col = column.trim().replace(/^"|"$/g, '').toLowerCase();
              map.set(`${table}.${col}`, entry.name);
            }
          }
        }
      }
    };
    walk(dir);
  }
  uniqueByDialect.set(dialect, map);
}

/* -------------------------------------------------------------------- 判定 */

check(
  '扫到了源码与三方言迁移脚本',
  sources.length > 0 && DIALECTS.every((d) => uniqueByDialect.get(d).size > 0),
  sources.length === 0
    ? '一个 `src/main/java` 源文件都没扫到 —— 目录结构变了？'
    : `扫描 ${sources.length} 个源文件 · ` +
      DIALECTS.map((d) => `${d} ${uniqueByDialect.get(d).size} 列`).join(' · '),
);

// ① 每个被引用的 *_EXISTS 码都登记了列
const unregistered = [...referenced].filter((code) => !JUDGED.has(code));
check(
  '每个被引用的判重码都登记了它判的哪一列',
  unregistered.length === 0,
  unregistered.length > 0
    ? `这些码有抛出/引用但不在 verify-unique-columns.mjs 的 JUDGED 里：${unregistered.join('、')}` +
      ' —— 先回答 0062 的问题（这个列在语义上指认什么），再来登记'
    : `${JUDGED.size} 个码逐条对应`,
);

// ② 登记的码仍然被引用（白名单腐烂，同 verify-result-codes 的口径）
const stale = [...JUDGED.keys()].filter((code) => !referenced.has(code));
check(
  '登记的判重码都还有落点',
  stale.length === 0,
  stale.length > 0
    ? `这些码在 JUDGED 里但源码已无引用（判重被删了？）：${stale.join('、')} —— 请同步删掉登记` +
      '；若这是 0062 式"有意不再判重"，删登记、把列挪进 NO_INDEX_ALLOWED 写明理由'
    : undefined,
);

// ③ 三方言的唯一索引集合一致（0038 的同构此前没人守）
const keysOf = (d) => [...uniqueByDialect.get(d).keys()].sort().join('\n');
const dialectKeys = DIALECTS.map(keysOf);
const consistent = dialectKeys.every((keys) => keys === dialectKeys[0]);
check(
  '三方言的唯一索引集合一致',
  consistent,
  consistent
    ? `集合：${dialectKeys[0].split('\n').join('、')}`
    : DIALECTS.map((d, i) => (dialectKeys[i] === dialectKeys[0] ? null : `${d} 独有/缺失`))
        .filter(Boolean)
        .join('；') + ' —— 改索引要三份同改（docs/decisions/0038）',
);

// ④ 判重列：要么有 UNIQUE，要么在白名单
const uniqueSet = uniqueByDialect.get('h2');
const judgedWithIndex = [];
const judgedWithoutIndexMissing = [];
const whitelistStale = [];
for (const [code, { table, column }] of JUDGED) {
  const key = `${table}.${column}`;
  if (uniqueSet.has(key)) {
    judgedWithIndex.push(key);
  } else if (NO_INDEX_ALLOWED.has(key)) {
    // 在白名单里，合法
  } else {
    judgedWithoutIndexMissing.push(key);
  }
}
for (const key of NO_INDEX_ALLOWED.keys()) {
  if (uniqueSet.has(key)) whitelistStale.push(key);
}

check(
  '每个判重列要么有 UNIQUE 索引，要么在白名单写明理由',
  judgedWithoutIndexMissing.length === 0,
  judgedWithoutIndexMissing.length === 0
    ? `有索引 ${judgedWithIndex.length} 列（数据约束级）· 白名单 ${NO_INDEX_ALLOWED.size} 列（业务规则级）`
    : `这些列在服务层判重、库上却没有 UNIQUE、也不在白名单：${judgedWithoutIndexMissing.join('、')}` +
      ' —— 要么加 UNIQUE（想清楚软删语义，见 0056），要么在 NO_INDEX_ALLOWED 写明为何无索引',
);
check(
  '白名单没有腐烂',
  whitelistStale.length === 0,
  whitelistStale.length === 0
    ? `名单 ${NO_INDEX_ALLOWED.size} 条，全部仍无索引`
    : `这些列已经有了 UNIQUE 索引，白名单理由已过期：${whitelistStale.join('、')}`,
);

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  "挡不挡重名"此前只能靠人逐个回溯源码与 DDL 才对得上账，现在有机器兜底。');
console.log('  它守的是"判重列与唯一索引对账"；"该不该判重"的判据见 docs/decisions/0062。');
