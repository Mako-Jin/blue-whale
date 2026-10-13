#!/usr/bin/env node
/**
 * 文档索引一致性自检（`docs/README.md` §四 当前文档索引）。
 *
 * ## 为什么需要它
 *
 * 本仓有一条硬性要求：**新增一份 ADR 必须同时把它登记进 `docs/README.md` 的索引**
 * （以及"改哪读哪"那张表）。这条要求此前**只靠人记得**，而它恰好是最容易漏的一步 ——
 * 写 ADR 时注意力全在正文上，索引是收尾的那一行字。
 *
 * 漏了不会立刻出事，这正是它危险的地方：索引少一行，没人会察觉。代价在很久以后才兑现 ——
 * 有人想知道"我们为什么这么设计"时翻不到，于是**重新讨论一遍**，或者更糟：
 * 照着一段已被推翻的旧决策又实现一遍。
 *
 * ## 它查什么
 *
 * | 这类错 | 症状 |
 * | --- | --- |
 * | 新增 ADR 但忘了登记 | 索引里查不到这份决策 |
 * | 索引指向不存在的文档 | 死链（改名、删文件之后） |
 * | ADR 编号跳号 | 破坏「`NNNN` 从 `0001` 递增、只增不改」这条约定 |
 * | §四 里其他路径写错 | 同上，且它们指向的是**活文档**（各 README），错得更隐蔽 |
 *
 * ## 用法
 *
 *     node scripts/verify-docs-index.mjs
 *
 * 不需要任何前置条件（不连网、不读产物、不写文件），失败时非零退出 —— 可以直接用在 CI 里。
 *
 * ## 刻意不查的
 *
 * §二"改哪读哪"表里引用的路径。那里混着 `decisions/0012` 这种**不带扩展名的简写**、
 * `sts-server-auth` 这种**模块名**、以及 `packages/*` 这种**通配** —— 要一律判成路径就得写
 * 很复杂的规则，而规则一复杂就会出现假阳性；**会误报的检查最终一定会被关掉**（关掉之后
 * 它保护不了任何东西）。所以只查 §四：那一节每一行的第一格**就是一个真实的文件路径**。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DOCS = join(ROOT, 'docs');
const INDEX_FILE = join(DOCS, 'README.md');
const DECISIONS_DIR = join(DOCS, 'decisions');

const problems = [];
const notes = [];

function check(label, ok, detail) {
  console.log(`${ok ? '✓' : '✗'} ${label}`);
  if (detail) console.log(`    ${detail}`);
  if (!ok) problems.push(`${label}${detail ? ` —— ${detail}` : ''}`);
}

/* ------------------------------------------------------------ 解析索引表格 */

const lines = readFileSync(INDEX_FILE, 'utf8').split(/\r?\n/);
const sectionStart = lines.findIndex((line) => /^##\s+四、/.test(line));
if (sectionStart < 0) {
  console.log('✗ 找不到 `docs/README.md` 的「四、当前文档索引」一节');
  process.exit(1);
}
const nextSection = lines.findIndex((line, index) => index > sectionStart && /^##\s/.test(line));
const section = lines.slice(sectionStart, nextSection < 0 ? lines.length : nextSection);

// 表格行：第一格用反引号包着的那个路径。
const rows = [];
for (const line of section) {
  const matched = /^\|\s*`([^`]+)`\s*\|/.exec(line);
  if (matched) rows.push(matched[1]);
}
if (rows.length === 0) {
  console.log('✗ 「四、当前文档索引」的表里一行路径都没解析出来 —— 表格结构或标题措辞变了？');
  process.exit(1);
}

const isAdrName = (name) => /^\d{4}-.+\.md$/.test(name);
const adrRows = rows.filter((path) => path.startsWith('decisions/'));
const otherRows = rows.filter((path) => !path.startsWith('decisions/'));

/* ------------------------------------------------- 一、ADR 索引双向一致 */

const onDisk = readdirSync(DECISIONS_DIR)
  .filter(isAdrName)
  .sort();
const inIndex = [...new Set(adrRows.map((path) => path.slice('decisions/'.length)))].filter(isAdrName);

const notIndexed = onDisk.filter((name) => !inIndex.includes(name));
const dangling = inIndex.filter((name) => !onDisk.includes(name));

check(
  '每篇 ADR 都登记在索引里',
  notIndexed.length === 0,
  notIndexed.length > 0
    ? `未登记：${notIndexed.join('、')} —— 新增 ADR 时必须同一次提交改 docs/README.md`
    : `${onDisk.length} 篇，逐条对应`,
);
check(
  '索引不指向不存在的 ADR',
  dangling.length === 0,
  dangling.length > 0 ? `死链：${dangling.join('、')}` : undefined,
);

/* ------------------------------------------------------------ 二、编号连续 */

const numbers = onDisk.map((name) => Number(name.slice(0, 4)));
const outOfOrder = numbers.filter((value, index) => value !== index + 1);
check(
  'ADR 编号从 0001 连续递增',
  outOfOrder.length === 0,
  outOfOrder.length > 0
    ? `偏离「从 0001 起、只增不改」的位置：第 ${outOfOrder
        .map((value, index) => `${index + 1} 篇是 ${String(value).padStart(4, '0')}`)
        .join(' · ')}`
    : `0001 ~ ${String(numbers.at(-1) ?? 0).padStart(4, '0')}`,
);

/* -------------------------------------------------- 三、其余条目都是真路径 */

// 表格里的路径两种写法混用：相对 `docs/`（`architecture/…`、`api/README.md`）
// 与相对仓库根（`../web/…`、`../scripts/README.md`）。两个基准都试。
const unresolved = otherRows.filter(
  (path) => !existsSync(resolve(DOCS, path)) && !existsSync(resolve(ROOT, path)),
);
check(
  '索引里引用的其他文档都存在',
  unresolved.length === 0,
  unresolved.length > 0 ? `找不到：${unresolved.join('、')}` : `${otherRows.length} 条路径`,
);

if (notes.length > 0) notes.forEach((note) => console.log(`    ${note}`));

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  文档索引与实际文件一致 —— 这条要求此前只能靠人记得，现在有机器兜底。');
