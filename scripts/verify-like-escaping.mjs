#!/usr/bin/env node
/**
 * 搜索关键词的转义 —— `server/` 里每一处 `LIKE` 都必须经过 `KeywordLike`，不许再有裸的 `like(...)`。
 *
 * ## 为什么需要它
 *
 * 搜索框的关键词是拼进 `LIKE '%…%'` 的，而 MyBatis-Plus 的 `like()` 只做 `%+值+%`、**不转义**
 * ⇒ 用户在搜索框里输 `%` 会匹配**全部行**、输 `_` 会匹配任意单字符（`docs/decisions/0067`
 * 有实测与用例）。修法是把 `%` `_` 与转义符本身转义，并把转义符**写进 SQL**
 * （`col LIKE {0} ESCAPE '!'`），这件事被收在 `KeywordLike` 这一个类里。
 *
 * 于是这里只剩一个会腐烂的地方：**新加一个搜索时又写了 `.like()`**。
 * 那种写法读起来完全正常（本仓此前 15 处都是它），测试也照样绿（新搜索本来就没有
 * "搜不到"那一支断言），只有"输 % 列出全表"这个没人会去试的角落会露出来。
 * 它跟 `0063` 那类守卫同性质：锚定的是**一个无歧义的 token 形状**（`.like(` 这个方法名），
 * 扫到就是扫到 —— 不是 `0064` 那种要理解 JSX 树、会误报的语义判定。
 *
 * ## 它查什么
 *
 * 1. `server/*` 的 `src/main/java` 里**没有任何裸的 `like*` 调用**，除非在 `ALLOWED` 里
 *    登记过（登记的动作就是回答"这个实参是从哪来的"）。
 * 2. 带有 `LIKE` 的 SQL 片段**同一行里必须有 `ESCAPE`** —— 挡住"手写一个 LIKE 而忘了声明
 *    转义符"。这一条同时守住 `KeywordLike` 自己：哪天它的片段被改回裸 `LIKE`，这里会红。
 *
 * ## 用法
 *
 *     node scripts/verify-like-escaping.mjs
 *
 * 不需要任何前置条件（只读源码文本，不编译、不连网、不写文件），失败时非零退出。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **`src/test` 下的 `like*`**：测试里自己造查询是为了验别的东西，不是生产路径。
 *   当前一处都没有；真有需要时再加白名单，别让它默默变红。
 * - **转义符到底选得对不对、`ESCAPE` 子句在三方言上是否同义**：那是语义，靠
 *   `SearchWildcardApiTest`（跨 profile）钉，不靠文本扫描（`0064` 的判据：
 *   会误报的守卫比没有更糟）。
 * - **`.apply(...)` 传进去的实参有没有真的转过义**（是不是 `KeywordLike.pattern` 的产物）：
 *   要判这个就得做数据流分析，误报率不划算。这里只守"调用形状"。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SERVER = join(ROOT, 'server');

/**
 * 允许存在的裸 `like*` 调用 —— 每条都必须写明"实参是从哪来的"，以及**这种调用有几处**。
 *
 * 计数不是装饰：只登记"形态"的话，两处同名调用里改掉一处、另一处还在，
 * "白名单没有腐烂"照样报绿 —— 那是 `0055` 说过的"只钉一支等于没钉"。
 * 所以这里逐条对账条数；真要多加一处同类调用，就来把数改掉 —— 改数的动作
 * 正是"再看一遍它的实参是不是用户输入"的复查点。
 *
 * 现在只有两条，而且都是**服务端自己拼的**层级前缀（不是用户输入）：
 * 组织树的 `code_path` 由「各级**主键**加斜杠」拼成（`OrganizationService#pathOf`，形如 `/1/5/`），
 * 里面只有数字与 `/` —— 用户手打的通配符进不来（注意 `org_code` 本身允许下划线，
 * 但 `code_path` 里**不含**编码）。它们走 `likeRight` 是为了「子树前缀匹配」，
 * 语义上也不是「搜索」。
 */
const ALLOWED = [
  {
    file: 'OrganizationService.java',
    arg: /^OrganizationEntity::getCodePath$/,
    count: 2,
    reason: '组织树的层级前缀：实参是「各级主键加斜杠」（#pathOf），只有数字与 /，不是用户输入（decisions/0067）',
  },
];

const problems = [];

function check(label, ok, detail) {
  console.log(`${ok ? '✓' : '✗'} ${label}`);
  if (detail) console.log(`    ${detail}`);
  if (!ok) problems.push(`${label}${detail ? ` —— ${detail}` : ''}`);
}

/* ------------------------------------------------------------------ 扫源码 */

function stripComments(text) {
  // 块注释**按字符数换成空格、保留换行**：直接删掉会让后面所有行号整体前移，
  // 而"报出来的行号"正是这个脚本唯一的可用线索。
  return text
    .replace(/\/\*[\s\S]*?\*\//g, (matched) => matched.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1 ');
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

// 调用点：`.like(` / `.likeRight(` / `.likeLeft(` / `.notLike*(`（`notLike` 同理会放走通配符）
const LIKE_CALL_RE = /\.(notLikeLeft|notLikeRight|notLike|likeLeft|likeRight|like)\s*\(/g;

const sources = collectMainSources();
const bareCalls = [];
const allowedHits = new Map(ALLOWED.map((entry) => [entry, 0]));
const likeWithoutEscape = [];

for (const file of sources) {
  const raw = readFileSync(file, 'utf8');
  const lines = stripComments(raw).split('\n');
  const name = file.slice(Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\')) + 1);

  lines.forEach((line, index) => {
    for (const matched of line.matchAll(LIKE_CALL_RE)) {
      // 取第一个实参（到该层第一个逗号或右括号为止）；`::` 不带逗号，够用
      const rest = line.slice(matched.index + matched[0].length);
      const arg = rest.split(/[,)]/)[0].trim();
      const allowed = ALLOWED.find((entry) => entry.file === name && entry.arg.test(arg));
      if (allowed) {
        allowedHits.set(allowed, allowedHits.get(allowed) + 1);
      } else {
        bareCalls.push({ file, line: index + 1, method: matched[1], arg });
      }
    }

    // 带 LIKE 的 SQL 片段必须同一声明转义符
    if (/\bLIKE\b/.test(line) && !/\bESCAPE\b/.test(line)) {
      likeWithoutEscape.push({ file, line: index + 1, text: line.trim() });
    }
  });
}

const rel = (file) => file.slice(ROOT.length + 1).replace(/\\/g, '/');

/* -------------------------------------------------------------------- 判定 */

check(
  '扫到了 `server/` 的源码',
  sources.length > 0,
  sources.length === 0 ? '一个 `src/main/java` 源文件都没扫到 —— 目录结构变了？' : `扫描 ${sources.length} 个源文件`,
);

check(
  '没有裸的 `like*` 调用（通配符会漏出去）',
  bareCalls.length === 0,
  bareCalls.length === 0
    ? `白名单 ${ALLOWED.length} 处（都是服务端拼的层级前缀）`
    : bareCalls
        .map(
          (item) =>
            `${rel(item.file)}:${item.line} 的 .${item.method}(${item.arg}…)` +
            ' —— 搜索关键词请改成 KeywordLike.pattern(...) + KeywordLike.condition(...)（decisions/0067）',
        )
        .join('\n    '),
);

check(
  '白名单没有腐烂',
  ALLOWED.every((entry) => allowedHits.get(entry) === entry.count),
  ALLOWED.every((entry) => allowedHits.get(entry) === entry.count)
    ? `${ALLOWED.length} 条登记、共 ${ALLOWED.reduce((sum, entry) => sum + entry.count, 0)} 处，都对得上`
    : ALLOWED.filter((entry) => allowedHits.get(entry) !== entry.count)
        .map(
          (entry) =>
            `${entry.file} 的 ${entry.arg.source}：登记 ${entry.count} 处、实际扫到 ${allowedHits.get(entry)} 处` +
            ' —— 那条调用被删/被改了形状就同步改登记；真多了一处同类调用，也来把它加上（顺手复查实参是不是用户输入）',
        )
        .join('\n    '),
);

check(
  'SQL 里出现 LIKE 的地方都声明了 ESCAPE',
  likeWithoutEscape.length === 0,
  likeWithoutEscape.length === 0
    ? '没有"手写 LIKE 却忘了转义符"的片段'
    : likeWithoutEscape
        .map((item) => `${rel(item.file)}:${item.line} —— ${item.text.slice(0, 80)}`)
        .join('\n    ') + ' —— 裸 LIKE 的转义符是数据库默认值，MySQL 的 NO_BACKSLASH_ESCAPES 会静默改掉它',
);

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  它守的是"关键词进 LIKE 之前有没有转义"这件事只有一种写法；');
console.log('  转义值不值得这么做的判据、以及为什么转义符不是反斜杠，见 docs/decisions/0067。');
