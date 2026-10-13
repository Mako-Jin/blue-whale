#!/usr/bin/env node
/**
 * 契约清单一致性自检（`docs/api/README.md` §一 vs `docs/api/openapi.json`）。
 *
 * ## 为什么需要它
 *
 * `docs/api/README.md` §一 手写了一份接口清单：总览句里的"路径 / 接口 / 模型"三个数、
 * 一张按模块的汇总表、以及逐域逐条列出的 `方法 + 路径`。按 `0039` 的判据
 * （**它会不会因为一次普通提交而变？**）这些东西**全都会漂** —— 新增一个接口就是一次普通提交。
 *
 * 它们与"测试用例数"的唯一区别是**有权威源**：契约文件 `docs/api/openapi.json` 是从运行的
 * 应用导出的，清单里每一条都能从它算出来。所以处置不是"删掉不要写"，而是**钉住**
 * （`0039` 决策的后续约束：有权威源的值 → 加一个零前置守卫核对，而不是靠人记得改）。
 *
 * ## 分两层查，因为两层的"漂"能见度不同
 *
 * 三个总数漂了，多少还有点对照（汇总表之和、后端模块数）；**逐条清单漂了没人看得出来** ——
 * 少一行看起来和"这个域本来就这么几个接口"完全一样。所以清单本身也要查，不只是查总数。
 *
 * | 这类错 | 症状 |
 * | --- | --- |
 * | 后端加了接口、文档没登记 | 文档的清单比契约少一条（或总数小 1） |
 * | 后端删了接口、文档没删 | 文档里列着一条契约里不存在的路径（读者照着它去调，404） |
 * | 改了路径或方法（`PUT` → `POST`） | 同一条接口在两边写法不一致，两边清单各"看着对" |
 * | 明细表加了行、汇总表的接口数忘了改 | 汇总表与明细表自相矛盾（`0039` 里那张 §七 表就是这一类） |
 * | 汇总行的分项之和与它自己写的数不符 | 汇总行内部就打脸 |
 * | 文档里重复列了同一条接口 | 行数多于条目数，计数全偏 |
 * | 汇总表里的模块名被改错 / 模块被改名 | 指向一个不存在的 Maven 模块 |
 *
 * ## 用法
 *
 *     node scripts/verify-api-contract.mjs
 *
 * **零前置条件**：不连网、不起服务、不读构建产物、不写文件。只读 `docs/` 下的两个文件，
 * 失败时非零退出 —— 因此可以直接挂在流水线的 `guards` job 里（同 `verify-docs-index.mjs`）。
 *
 * ## 一条硬规则：**解析不出来 = 失败**
 *
 * 这个脚本是靠正则去读 Markdown 的。表格改版、标题措辞变了，正则就会**匹配不到** ——
 * 那时如果"匹配不到就跳过"，它会在什么都没查的情况下报"通过"。那是**又一个空转的检查**，
 * 比没有检查更坏（它让人以为有东西在守）。所以任何一处解析不出预期结构，都直接非零退出，
 * 并指出是**哪一处、期望什么样的结构**。
 *
 * ## 刻意不查的
 *
 * - **`@Operation` 文案、字段说明、`description`**：它们是给人读的散文，判不了对错。
 *   契约里改了描述文案而文档没跟，不在此脚本射程内。
 * - **模型名**：`docs/api/README.md` 的「关键数据模型」表是**精选**（62 个名字 vs 契约 100 个），
 *   而且里面混着 `UserGroupQuery` 这类**被 Springdoc 摊平成查询参数、压根不是 schema** 的名字
 *   （`AuditLogQuery` / `OrganizationQuery` / `OrgMemberQuery` / `UserGroupMemberQuery` / `AuditResult`）。
 *   要判它们就得维护一张白名单，而**会误报的检查终将被关掉**（同 `0036` 决策 5 的理由）。
 *   所以只查**总数**（100 个数据模型 = `components.schemas` 的条目数），不查名字。
 * - **§五 待补充里的历史陈述**（"已补齐到 20 条路径"这类）。那是**记录当次进度**的句子，
 *   不是"当前清单"—— 与 ADR 同性质。
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SPEC_FILE = join(ROOT, 'docs', 'api', 'openapi.json');
const DOC_FILE = join(ROOT, 'docs', 'api', 'README.md');
const SERVER_DIR = join(ROOT, 'server');

const HTTP_METHODS = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options', 'trace'];

const problems = [];

function check(label, ok, detail) {
  console.log(`${ok ? '✓' : '✗'} ${label}`);
  if (detail) console.log(`    ${detail}`);
  if (!ok) problems.push(`${label}${detail ? ` —— ${detail}` : ''}`);
}

function bail(message, hint) {
  console.log(`✗ ${message}`);
  if (hint) console.log(`    ${hint}`);
  console.log('');
  console.log('✗ 自检中止 —— 读不到契约或读不懂文档，都不能算通过');
  process.exit(1);
}

/* ------------------------------------------------------------- 读权威源 */

let spec;
try {
  spec = JSON.parse(readFileSync(SPEC_FILE, 'utf8'));
} catch (error) {
  bail(`读不到或解析不了 \`docs/api/openapi.json\``, `${error.message}`);
}
if (!spec.paths || typeof spec.paths !== 'object' || Object.keys(spec.paths).length === 0) {
  bail('契约里没有 `paths`（或它是空的）', '契约文件损坏时不能报"通过"');
}
const schemaNames = Object.keys(spec.components?.schemas ?? {});
if (schemaNames.length === 0) {
  bail('契约里没有 `components.schemas`（或它是空的）', '同上');
}

/** 契约里的全部接口，`"GET /api/xxx"` 形态。 */
const specOps = new Map();
for (const [path, item] of Object.entries(spec.paths)) {
  if (!item || typeof item !== 'object') continue;
  for (const method of Object.keys(item)) {
    if (!HTTP_METHODS.includes(method)) continue;
    specOps.set(`${method.toUpperCase()} ${path}`, { method: method.toUpperCase(), path });
  }
}
if (specOps.size === 0) bail('契约里一个接口都没解析出来', '`paths` 里没有任何 HTTP 方法？');

const specPaths = Object.keys(spec.paths).length;
const specModels = schemaNames.length;

/* ------------------------------------------------------------- 读被守卫的文档 */

const lines = readFileSync(DOC_FILE, 'utf8').split(/\r?\n/);

const sectionOne = lines.findIndex((line) => /^##\s+一、/.test(line));
if (sectionOne < 0) {
  bail('找不到 `docs/api/README.md` 的「一、当前接口清单」一节', '标题措辞变了？本脚本按 `## 一、` 定位');
}
const sectionTwo = lines.findIndex((line, index) => index > sectionOne && /^##\s/.test(line));
const scope = sectionTwo < 0 ? lines.length : sectionTwo;

/* ----- 一、总览句里的三个数 ----- */

const headingLine = lines
  .slice(sectionOne, scope)
  .find((line) => line.includes('个接口') && line.includes('条路径') && line.includes('个数据模型'));

if (!headingLine) {
  bail(
    '在 §一 里找不到总览句（含"个接口 / 条路径 / 个数据模型"的那一句）',
    '它被删掉或改写了 —— 本脚本要核对的正是这句话里的三个数，找不到就不能报"通过"',
  );
}
const plain = headingLine.replace(/\*/g, '');
const heading = /共\s*(\d+)\s*个接口（\s*(\d+)\s*条路径\s*\/\s*(\d+)\s*个数据模型\s*）/u.exec(plain);
if (!heading) {
  bail(
    '总览句的写法读不懂，取不到"接口 / 路径 / 模型"三个数',
    `实际读到：${headingLine.trim()}\n    期望形态：共 **N 个接口**（**N 条路径** / **N 个数据模型**）`,
  );
}
const [headingOps, headingPaths, headingModels] = heading.slice(1).map(Number);

/* ----- 二、按模块的汇总表 ----- */

const summaryRows = [];
for (const line of lines.slice(sectionOne, scope)) {
  const matched = /^\|\s*([^|]+?)\s*\|\s*`([A-Za-z0-9_-]+)`\s*\|\s*(\d+)\s*\|(.*)\|\s*$/.exec(line);
  if (matched) {
    summaryRows.push({
      domain: matched[1].replace(/\*/g, '').trim(),
      module: matched[2],
      count: Number(matched[3]),
      note: matched[4],
    });
  }
}
if (summaryRows.length === 0) {
  bail(
    '§一 的按模块汇总表一行都没解析出来',
    '期望形态：| 域 | `模块名` | 接口数 | 说明 | —— 列顺序或写法变了？',
  );
}

/* ----- 三、逐域明细表里的 `方法 + 路径` ----- */

const detailRows = [];
let currentModule = null;
for (const line of lines.slice(sectionOne, scope)) {
  const header = /^###\s+(.+?)\s*（`([A-Za-z0-9_-]+)`）\s*$/.exec(line);
  if (header) {
    currentModule = header[2];
    continue;
  }
  if (/^#{2,3}\s/.test(line)) currentModule = null; // 关键数据模型那节等：不承载接口行
  const row = /^\|\s*`(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|TRACE)`\s*\|\s*`([^`]+)`\s*\|/.exec(line);
  if (row) {
    // ⚠️ 即使 `currentModule` 是 null 也要收进来：否则这一行会**从两处检查里同时消失**
    // （清单比对看不见它、按模块汇总也算不到它）—— 那就是一个静默通过的孔。
    detailRows.push({ key: `${row[1]} ${row[2]}`, module: currentModule, line: line.trim() });
  }
}
if (detailRows.length === 0) {
  bail(
    '§一 的逐域明细表一行接口都没解析出来',
    '期望形态：| `GET` | `/api/xxx` | … | —— 且所在小节的标题要写成 `### 域名（`模块名`）`',
  );
}

/* ------------------------------------------------------------------ 一 */

check(
  '总览句的"接口数"与契约一致',
  headingOps === specOps.size,
  headingOps === specOps.size ? `${headingOps} 个` : `文档写 ${headingOps}，契约实测 ${specOps.size}`,
);
check(
  '总览句的"路径数"与契约一致',
  headingPaths === specPaths,
  headingPaths === specPaths ? `${headingPaths} 条` : `文档写 ${headingPaths}，契约实测 ${specPaths}`,
);
check(
  '总览句的"数据模型数"与契约一致',
  headingModels === specModels,
  headingModels === specModels
    ? `${headingModels} 个（= components.schemas 条目数）`
    : `文档写 ${headingModels}，契约实测 ${specModels}`,
);

/* ------------------------------------------------------------------ 二 */

const orphanRows = detailRows.filter((row) => row.module === null);
check(
  '每条明细行都落在 `### 域名（`模块名`）` 小节里',
  orphanRows.length === 0,
  orphanRows.length > 0
    ? `${orphanRows.length} 行没有归属：${orphanRows.map((row) => row.key).join('、')}\n    ` +
      '它们既算不进按模块的汇总、也无法与契约比对 —— 请把它们所在小节的标题改成 `### 域名（`模块名`）`'
    : undefined,
);

const docKeys = detailRows.map((row) => row.key);
const docSet = new Set(docKeys);
const duplicated = docKeys.filter((key, index) => docKeys.indexOf(key) !== index);
check(
  '明细表里没有重复列出的接口',
  duplicated.length === 0,
  duplicated.length > 0 ? `重复：${[...new Set(duplicated)].join('、')}` : `${detailRows.length} 条，无重复`,
);

const notInDoc = [...specOps.keys()].filter((key) => !docSet.has(key));
const notInSpec = [...docSet].filter((key) => !specOps.has(key));
check(
  '契约里的每条接口都登记在明细表里',
  notInDoc.length === 0,
  notInDoc.length > 0
    ? `${notInDoc.length} 条未登记：${notInDoc.join('、')}\n    新增接口时同一次提交改 docs/api/README.md §一`
    : `${specOps.size} 条，逐条对应`,
);
check(
  '明细表里没有契约里不存在的接口',
  notInSpec.length === 0,
  notInSpec.length > 0
    ? `${notInSpec.length} 条多余：${notInSpec.join('、')}\n    路径改名或方法改了，或接口已删而文档没跟`
    : undefined,
);

/* ------------------------------------------------------------------ 三 */

// 明细行按模块归拢 —— `sts-server-auth` 在文档里跨两节（权限域 + 组织域），要相加。
const byModule = new Map();
for (const row of detailRows) {
  if (row.module === null) continue; // 已在上面单独报过
  byModule.set(row.module, (byModule.get(row.module) ?? 0) + 1);
}

const summaryTotal = summaryRows.reduce((sum, row) => sum + row.count, 0);
check(
  '汇总表的接口数之和等于契约里的接口数',
  summaryTotal === specOps.size,
  summaryTotal === specOps.size ? `${summaryTotal} 个` : `汇总表合计 ${summaryTotal}，契约实测 ${specOps.size}`,
);

const wrongModuleCounts = summaryRows
  .map((row) => ({ ...row, actual: byModule.get(row.module) ?? 0 }))
  .filter((row) => row.count !== row.actual);
check(
  '汇总表每个模块的接口数等于该模块在明细表里的行数',
  wrongModuleCounts.length === 0,
  wrongModuleCounts.length > 0
    ? wrongModuleCounts
        .map((row) => `${row.module}：汇总写 ${row.count}、明细有 ${row.actual}`)
        .join('\n    ')
    : summaryRows.map((row) => `${row.module} ${row.count}`).join(' · '),
);

// 汇总行的"说明"里写了分项时（`认证 4 + 租户 5 + …`），分项之和要等于该行自己写的数。
const wrongNotes = [];
for (const row of summaryRows) {
  if (!row.note.includes('+')) continue;
  const parts = [...row.note.matchAll(/\d+/g)].map((item) => Number(item[0]));
  if (parts.length < 2) continue;
  const sum = parts.reduce((total, value) => total + value, 0);
  if (sum !== row.count) wrongNotes.push(`${row.module}：分项之和 ${sum}（${row.note.trim()}）≠ 它写的 ${row.count}`);
}
check(
  '汇总行里写了分项时，分项之和等于该行的接口数',
  wrongNotes.length === 0,
  wrongNotes.length > 0 ? wrongNotes.join('\n    ') : undefined,
);

/* ------------------------------------------------------------------ 四 */

// 汇总表里的模块名必须真有这个 Maven 模块（改名 / 写错都会在这里现形）。
const moduleDirs = new Set();
(function walk(dir, depth) {
  if (depth > 3) return;
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    if (entry.name === 'target' || entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (existsSync(join(full, 'pom.xml')) && statSync(full).isDirectory()) moduleDirs.add(entry.name);
    walk(full, depth + 1);
  }
})(SERVER_DIR, 0);

const unknownModules = summaryRows.map((row) => row.module).filter((name) => !moduleDirs.has(name));
check(
  '汇总表里的模块名都真实存在',
  unknownModules.length === 0,
  unknownModules.length > 0
    ? `server/ 下找不到：${unknownModules.join('、')}`
    : `${summaryRows.length} 个模块，逐个对上 pom.xml`,
);

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  清单与契约逐条对得上 —— 这份清单此前只能靠人记得改，现在有机器兜底。');
