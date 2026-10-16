#!/usr/bin/env node
/**
 * `404xx` 的文案自检 —— 这一族的码**只有一个来源**（枚举里那句话），抛出点不许另说一句。
 *
 * ## 为什么需要它
 *
 * `docs/decisions/0059` 为 `404xx` 立过一条形状：**一个码就是一个对象**（`40401` 用户 /
 * `40402` 租户 / …），所以名字住在枚举里、抛出点不写文案，也就写不出错的对象。
 * 由它推出两条规矩：① **在抛出点覆盖文案 = 这个码在说两件事**；② 断言这一族断 `code` 即完整。
 *
 * `0074` 把 ① 读成了"**可以**覆盖，只要覆盖的理由一样" ——
 * 它用一个辅助方法把"东西真的不存在"与"存在但不属于当前租户"合并成一句
 * （`notFoundCode.getMessage() + "或不属于当前租户"`），并把当时在
 * `DataScopeService#resolve` 的那一处推广成一族。**那个方向是反的**：
 * 覆盖之后，"真的不存在"那一支说的仍是裸文案，于是同一个码在两条分支上说出
 * **两句不同的话** —— 调用方比一下 `message` 就知道"这条记录确实存在，只是在别家"，
 * 而"它存在"本身就是别人的信息（`0025` 第 2 条）。`0075` 把那一族撤回。
 *
 * 撤回这件事**没有任何东西会拦**：`404xx` 的现有用例（`NotFoundCodeApiTest` 断文案前缀、
 * `ReferenceMessageApiTest` 断 `40002` 的短语）都断的是"**含有**某个词"，
 * 拦不住"想拦的那句话仍在"。所以它需要一个机械守卫。
 *
 * ## 它查什么（两条，都是**语法级**事实）
 *
 * | # | 规则 | 为什么它是错 |
 * | --- | --- | --- |
 * | A | 枚举里 `404xx` 的常量，凡是写成 `CONST.businessException(...)` 的，实参必须为空 | 一族的码都靠枚举那句话报错；补一句就是让这个码开始说两件事 |
 * | B | 任何 `businessException(...)` 的实参里不得出现 `.getMessage()` | 拿**另一个码**的文案来拼，等于把这个码的含义挂到那个码上 —— ⑨ `0074` 那一处正是这个形状 |
 *
 * 两条都不需要理解语义：一条是"调用点的实参为空"，一条是"实参里有没有那个方法名"。
 * 因此不会误报 —— 而**会误报的守卫比没有更糟**（`0064`）。
 *
 * ## 用法
 *
 *     node scripts/verify-notfound-messages.mjs
 *
 * 零依赖、不连网、不编译、不读产物，失败时非零退出。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **"每个带 `tenant_id` 的实体表，其 `requireXxx` 必须调 `assertVisible`"**。
 *   这是 `0074`「未覆盖 4」想要的那条守卫。本片写下这一段时给的结论是"写不了"——
 *   理由是**表名 → 类名不是无歧义的 token 形状**（`bw_auth_user` → `UserService#requireUser`、
 *   `bw_auth_organization` → `requireOrg`、`bw_auth_user_group` → `requireGroup`……），
 *   只能硬编码一张对照表。**那个结论是错的，`0076` 已把它证伪**：
 *   需要的不是"表名 → 方法名"（那是人起的名字），而是"**这个 `selectById` 取的是哪张表的行**" ——
 *   它在源码里有唯一答案，四环全是字面量（字段声明 → `BaseMapper<…>` 泛型 → `@TableName` → DDL）。
 *   守卫见 `scripts/verify-tenant-visibility.mjs`，零硬编码，例外由 DDL 推导。
 *   本片的那段论证留在仓库记忆里的方式，是 `0076` —— **ADR 不回改旧文**。
 * - **写侧"这次引用的对象与容器同租户吗"这类检查有没有走同一个落点**。
 *   手搓的写法无穷（`.equals(tenantId)`、`Objects.equals`、先取出局部变量再比、
 *   在 SQL 里 `eq(tenant_id)` ……），锚不住。本轮人工核了一遍（16 表 / 16 个去重引用列），
 *   那仍是当前唯一的办法，见 `docs/decisions/0075`。
 *   （`0076` 把其中**一种**形状锚住了 —— `getTenantId()` 直接参与 `equals` 的那两种写法，
 *   且射程限定在"采用了统一落点的模块"里。它不覆盖先取局部变量再比的写法。）
 * - **形参点调用时写的固定文案**。`assertVisible(code, …)` 里的常量是形参，名字扫不到；
 *   但那种写法要报一句有意义的话，几乎必然要拼另一个码的文案 —— 由规则 B 兜住。
 *   真正无解的只剩"形参调用却写一句与形参无关的固定文案"，而那种写法读起来就是错的
 *   （形参白传了），不需要机器来发现。
 * - **具体措辞**。规则守的是机制（这一族只有一个来源），不是"哪一句字面量" ——
 *   换句话说，重写枚举里那句话不会被它拦下，那是另一件事。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SERVER = join(ROOT, 'server');
const AUTH_RESULT_CODE =
  'server/sts-server/sts-server-auth/src/main/java/com/bluewhale/sts/auth/common/AuthResultCode.java';

const problems = [];
const notes = [];

function check(label, ok, detail) {
  console.log(`${ok ? '✓' : '✗'} ${label}`);
  if (detail) console.log(`    ${detail}`);
  if (!ok) problems.push(`${label}${detail ? ` —— ${detail}` : ''}`);
}

/* ------------------------------------------------------------------ 扫源码 */

/**
 * 去掉注释。
 *
 * 与 `verify-result-codes.mjs` 同一条理由与同一份实现：源码里到处是
 * `{@code AuthResultCode.USER_NOT_FOUND}` 这种**说明**，不剥掉的话，
 * 一句"详见 xxx 的说明"就能把一个违规的写法伪装成合规 ——
 * 本脚本要查的两条规则都可能在注释里被"正面举例"，所以这一步不是洁癖。
 */
function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/[^\n]*/g, '$1 ');
}

/** 收集 `server/<组>/<模块>/src/main/java` 下的全部 `.java` 文件 */
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

/**
 * 取出 `open`（指向某个 `(`）之后那个**配平**的实参文本。
 *
 * 必须配平而不是"取到第一个 `)`"：`businessException("a" + f(x))` 这种实参里
 * 自己带括号，截断在第一个 `)` 会让规则 B 把它读成合规。
 * 字符串字面量里的括号要跳过，否则 `"（见 x）"` 会打乱计数。
 */
function argumentOf(text, open) {
  let depth = 0;
  let quote = null;
  for (let i = open; i < text.length; i += 1) {
    const ch = text[i];
    if (quote) {
      if (ch === '\\') i += 1;
      else if (ch === quote) quote = null;
      continue;
    }
    if (ch === '"' || ch === "'") {
      quote = ch;
      continue;
    }
    if (ch === '(') depth += 1;
    else if (ch === ')') {
      depth -= 1;
      if (depth === 0) return text.slice(open + 1, i);
    }
  }
  return text.slice(open + 1);
}

/** 把字符下标换算成 `行:列`，让报告直接能点开 */
function locate(text, index) {
  const head = text.slice(0, index);
  const line = head.split('\n').length;
  const column = index - head.lastIndexOf('\n');
  return `${line}:${column}`;
}

const sources = collectMainSources();
if (sources.length === 0) {
  console.log('✗ 一个 `src/main/java` 源文件都没扫到 —— 目录结构变了？');
  process.exit(1);
}
if (!existsSync(resolve(ROOT, AUTH_RESULT_CODE))) {
  console.log(`✗ 找不到枚举文件：${AUTH_RESULT_CODE}`);
  process.exit(1);
}

/* ------------------------------------------------------------- 解析 404xx 名单 */

const enumText = stripComments(readFileSync(resolve(ROOT, AUTH_RESULT_CODE), 'utf8'));
const notFoundNames = [];
for (const matched of enumText.matchAll(/\b([A-Z][A-Z0-9_]*)\s*\(\s*(\d{5})\s*,/g)) {
  if (matched[2].startsWith('404')) notFoundNames.push({ name: matched[1], code: matched[2] });
}

if (notFoundNames.length === 0) {
  console.log('✗ 一个 `404xx` 常量都没解析出来 —— 枚举的写法变了？');
  process.exit(1);
}

console.log(`  扫描 ${sources.length} 个源文件 · ${notFoundNames.length} 个 404xx 的码`);
console.log(`    ${notFoundNames.map((item) => `${item.code} ${item.name}`).join(' · ')}`);

/* -------------------------------------------------------------------- 判定 */

const withArgument = [];
const crossMessage = [];

for (const file of sources) {
  const text = stripComments(readFileSync(file, 'utf8'));
  const shown = relative(ROOT, file).replace(/\\/g, '/');

  // 规则 A：404xx 的常量点调用，实参必须为空
  for (const { name, code } of notFoundNames) {
    const pattern = new RegExp(`\\b${name}\\s*\\.\\s*businessException\\s*\\(`, 'g');
    for (const matched of text.matchAll(pattern)) {
      const open = matched.index + matched[0].lastIndexOf('(');
      const argument = argumentOf(text, open).trim();
      if (argument !== '') {
        withArgument.push({ shown, at: locate(text, matched.index), name, code, argument });
      }
    }
  }

  // 规则 B：实参里不得出现 `.getMessage()`
  for (const matched of text.matchAll(/\bbusinessException\s*\(/g)) {
    const open = matched.index + matched[0].lastIndexOf('(');
    const argument = argumentOf(text, open);
    if (/\.\s*getMessage\s*\(/.test(argument)) {
      crossMessage.push({ shown, at: locate(text, matched.index), argument });
    }
  }
}

check(
  '404xx 的抛出点都是无参的（文案只能来自枚举）',
  withArgument.length === 0,
  withArgument.length > 0
    ? withArgument
        .map(
          (item) =>
            `${item.shown}:${item.at} ${item.code} ${item.name} 传了实参「${item.argument.trim()}」`,
        )
        .join(' / ') +
      ' —— 一个码两种说法，调用方比一下 message 就读出了"它存在，只是不在你这儿"（decisions/0059 · 0075）'
    : `${notFoundNames.length} 个码，全部只抛枚举里那句话`,
);

check(
  '没有一处 businessException 在拼别的码的文案',
  crossMessage.length === 0,
  crossMessage.length > 0
    ? crossMessage
        .map((item) => `${item.shown}:${item.at} 实参里出现 getMessage()「${item.argument.trim()}」`)
        .join(' / ') +
      ' —— 这让这个码的含义挂到另一个码上，正是 0074 那一处的形状（decisions/0075）'
    : '实参清一色是字面量',
);

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  "404xx 只有一个来源"此前靠 0059 的一句话与人工回溯，现在有机器兜底。');
console.log('  "每一处都有人断言"仍要人工 —— 见 docs/decisions/0075 的「未覆盖」。');
