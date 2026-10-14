#!/usr/bin/env node
/**
 * 错误码「有没有落点」自检 —— 枚举里的每个码，要么有代码会抛它，要么明写着"刻意不用"。
 *
 * ## 为什么需要它
 *
 * 本仓的错误码分两层：公共 {@code ResultCode}（每类别只占 `00` 号）与**域内细分**的
 * `AuthResultCode`。两边都是"先登记、再使用"，而**登记了却没有任何抛出点**这件事
 * 没有任何东西会发现：枚举里多一个码不影响编译，也不会让任何测试变红。
 *
 * 它的代价出现在很久以后。`docs/decisions/0059` 那轮回溯 `404xx` 时，逐个码在源码里数
 * 抛出点才看出六个地方没有断言；`0059` 的「未覆盖」第 5 条把这条记成了一个待办：
 * **"零断言的码"没有机械守卫**。本轮（`0060`）把 `401xx` / `403xx` 按同一口径扫了一遍，
 * 结果是**五个码一次抛出点都没有**（`40105` / `40107`~`40110`），外加公共层两个
 * （`40300` / `42900`）—— 它们此前谁都说不清是"漏了"还是"有意留着"。
 *
 * ## 它与"全称句守卫"的区别（这正是它值、而那一类不值的理由）
 *
 * `docs/decisions/0056` 把"给『全仓都……』加机械守卫"列进了**刻意不做**，理由是全称句的
 * 守卫要理解语义（"每个域都清了残留"机械判不了）。本脚本守的不是那个，而是一条**语法级**
 * 事实：*某个枚举常量的名字，在 `src/main` 的代码里有没有被引用过*。它可以逐字符精确判定、
 * 不依赖任何约定，因此不误报；而它挡住的是一种**只增不减**的退化 —— 码越加越多，
 * 没人记得哪个是活的。
 *
 * ## 它查什么
 *
 * | 这类错 | 症状 |
 * | --- | --- |
 * | 新加了码但没有抛出点、也没登记理由 | 前端照着文档分支，落在一个永远不成立的条件上 |
 * | 码本来有用、抛出点被删了 | 同上 —— 而且这次是**回归**，不是新错 |
 * | 白名单里的码重新有了抛出点 | 白名单腐烂：理由已经过期，却还在替这个码打掩护 |
 *
 * ## 用法
 *
 *     node scripts/verify-result-codes.mjs
 *
 * 不需要任何前置条件（不连网、不编译、不读产物），失败时非零退出。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **断言**。"有抛出点"与"有用例钉住"是两件事，本脚本只守前者 —— 它是后者的**前置条件**，
 *   但"有抛出点却没有断言"机械判不了（断言形式太多：`value(40101)`、`AuthResultCode.X.getCode()`、
 *   变量、`containsString`……）。`0059` 那轮人工扫过一次 `404xx` 的六处空缺，那仍是当前唯一的办法。
 * - **前端读了哪些码**。那是另一条判据（跨端），本脚本只看后端 `src/main`。
 * - **注释里提到的码**。源码里的 `{@code AuthResultCode.USER_NOT_FOUND}` 是**说明**不是**抛出点**，
 *   所以扫描前会先把注释去掉 —— 否则一句"详见 xxx 的说明"就能把一个零落点的码伪装成有落点。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SERVER = join(ROOT, 'server');

/** 两个枚举：公共层与权限域。加新枚举时在这里登记一行即可。 */
const ENUMS = [
  {
    type: 'ResultCode',
    label: '公共枚举',
    file: 'server/sts-common/sts-common-basic/src/main/java/com/bluewhale/sts/common/basic/enums/ResultCode.java',
  },
  {
    type: 'AuthResultCode',
    label: '权限域枚举',
    file: 'server/sts-server/sts-server-auth/src/main/java/com/bluewhale/sts/auth/common/AuthResultCode.java',
  },
];

/**
 * 没有落点的码 —— 分两种，每一条都必须写明理由。
 *
 * - `intentional`：**有意如此**，不该改。改动它等于改语义，而那条语义已经在别处表达过了。
 * - `pending`：**登记了但功能没做**。守卫在这里同时充当一份清单 —— "码已经先修好了，
 *   路还没修"，而这个形状此前只能靠读代码时偶然想起来。
 *
 * 不写理由就不许进这张表：没有理由的白名单只是把一个错误藏起来，而它藏住的东西
 * 下次仍然要重新推一遍。清单本身也该随着理由消失而缩短。
 */
const UNUSED = new Map([
  [
    'AuthResultCode.TOKEN_EXPIRED',
    {
      kind: 'intentional',
      reason:
        '"过期"在三处被有意合并：访问令牌侧由 AuthTokenFilter 一并按"未认证"处理（它只翻译不拒绝，' +
        '最终由 AuthInterceptor 抛 40100）、刷新令牌侧由 AuthService#refresh 并入 40106、' +
        '登出侧按幂等成功处理。JwtExpiredException 仍保留区分能力，但没有使用者',
    },
  ],
  [
    'ResultCode.FORBIDDEN',
    {
      kind: 'intentional',
      reason: '权限拒绝全走域码 40301（见 AuthInterceptor）；本码是公共层同一语义的占位，没有读者',
    },
  ],
  [
    'ResultCode.TOO_MANY_REQUESTS',
    {
      kind: 'intentional',
      reason: '限流落在 nginx 层（docs/decisions/0028），应用层不抛；留给"网关之外再限流"的场合',
    },
  ],
  [
    'AuthResultCode.CAPTCHA_REQUIRED',
    { kind: 'pending', reason: '验证码功能未实现' },
  ],
  [
    'AuthResultCode.CAPTCHA_INVALID',
    { kind: 'pending', reason: '验证码功能未实现' },
  ],
  [
    'AuthResultCode.MFA_REQUIRED',
    { kind: 'pending', reason: '多因子未实现（bw_auth_user.mfa_enabled 也只回显）' },
  ],
  [
    'AuthResultCode.MFA_CODE_INVALID',
    { kind: 'pending', reason: '多因子未实现' },
  ],
  [
    'AuthResultCode.ROLE_NAME_EXISTS',
    {
      kind: 'pending',
      reason:
        '角色名称唯一性未实现。同一个域的 ROLE_CODE_EXISTS 有落点、租户侧的 TENANT_NAME_EXISTS 也有 —— ' +
        '这三个"名称已存在"码是 docs/decisions/0056 未覆盖 4（"*_name 该不该唯一"）的具体形状',
    },
  ],
  [
    'AuthResultCode.ORG_NAME_EXISTS',
    { kind: 'pending', reason: '组织名称唯一性未实现（同 ROLE_NAME_EXISTS）' },
  ],
  [
    'AuthResultCode.USER_GROUP_NAME_EXISTS',
    { kind: 'pending', reason: '用户组名称唯一性未实现（同 ROLE_NAME_EXISTS）' },
  ],
]);

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
 * 这一步不是洁癖：源码里到处是 `{@code AuthResultCode.ORG_NAME_EXISTS}` 这种引用，
 * 而它们是**说明**。不剥掉的话，一个零落点的码只要在注释里被提过一次，就会被算成"有落点"——
 * 守卫会安静地放过它要挡的那一类错。
 *
 * 块注释整段删；行注释用一个 `[^:]` 保护，免得把 `https://` 里的斜杠认成注释起点。
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

const sources = collectMainSources();
const enumFiles = new Set(ENUMS.map((item) => resolve(ROOT, item.file)));

// 一次遍历把"谁被引用了"收全：`EnumType.CONSTANT` 两种枚举用同一个正则。
const referenced = new Set();
for (const file of sources) {
  const code = stripComments(readFileSync(file, 'utf8'));
  for (const matched of code.matchAll(/\b(ResultCode|AuthResultCode)\.([A-Z][A-Z0-9_]*)/g)) {
    referenced.add(`${matched[1]}.${matched[2]}`);
  }
}

/* ------------------------------------------------------------- 解析枚举定义 */

const declared = [];
for (const item of ENUMS) {
  const path = resolve(ROOT, item.file);
  if (!existsSync(path)) {
    console.log(`✗ 找不到枚举文件：${item.file}`);
    process.exit(1);
  }
  const text = stripComments(readFileSync(path, 'utf8'));
  for (const matched of text.matchAll(/\b([A-Z][A-Z0-9_]*)\s*\(\s*(\d+)\s*,/g)) {
    declared.push({ full: `${item.type}.${matched[1]}`, type: item.type, label: item.label, name: matched[1], code: Number(matched[2]) });
  }
}

if (declared.length === 0) {
  console.log('✗ 两个枚举里一个常量都没解析出来 —— 定义格式变了？');
  process.exit(1);
}
if (sources.length === 0) {
  console.log('✗ 一个 `src/main/java` 源文件都没扫到 —— 目录结构变了？');
  process.exit(1);
}

/* -------------------------------------------------------------------- 判定 */

const unused = declared.filter((item) => !referenced.has(item.full));
const unusedButNotListed = unused.filter((item) => !UNUSED.has(item.full));
const listedButUsed = [...UNUSED.keys()].filter((full) => referenced.has(full));
const staleAllowlist = listedButUsed.filter((full) => declared.some((item) => item.full === full));
const unknownAllowlist = [...UNUSED.keys()].filter(
  (full) => !declared.some((item) => item.full === full),
);

const pad = (value, width) => {
  // CJK 算 2 个显示宽度，与仓库文档表格的口径一致
  const shown = [...value].reduce((sum, ch) => sum + (/[\u4e00-\u9fff]/.test(ch) ? 2 : 1), 0);
  return value + ' '.repeat(Math.max(0, width - shown));
};

console.log(`  扫描 ${sources.length} 个源文件 · 两个枚举共 ${declared.length} 个码`);
for (const item of ENUMS) {
  const total = declared.filter((row) => row.type === item.type).length;
  const dead = unused.filter((row) => row.type === item.type).length;
  console.log(`    ${pad(item.label, 12)}${pad(item.type, 16)}${total} 个码，${dead} 个没有落点`);
}

if (unused.length > 0) {
  const groups = [
    ['pending', '登记了但功能没做（是待办，不是有意留白）：'],
    ['intentional', '有意不用（改动它等于改语义，而那条语义已在别处表达）：'],
  ];
  for (const [kind, title] of groups) {
    const rows = unused.filter((item) => UNUSED.get(item.full)?.kind === kind);
    if (rows.length === 0) continue;
    console.log(`  ${title}`);
    for (const item of rows) {
      const reason = UNUSED.get(item.full)?.reason ?? '';
      console.log(`    ${pad(`${item.code}`, 7)}${pad(item.full, 40)}${reason.split('。')[0].slice(0, 44)}`);
      notes.push(`${item.code} ${item.full}`);
    }
  }
}

check(
  '每个登记了理由的码都真的有理由',
  unknownAllowlist.length === 0,
  unknownAllowlist.length > 0
    ? `白名单里这些名字在枚举里不存在（改名了？）：${unknownAllowlist.join('、')}`
    : undefined,
);
check(
  '每个码要么有落点，要么明写着"刻意不用"',
  unusedButNotListed.length === 0,
  unusedButNotListed.length > 0
    ? `没有落点又没登记：${unusedButNotListed.map((item) => `${item.code} ${item.full}`).join('、')}` +
      ' —— 要么接上抛出点，要么在 scripts/verify-result-codes.mjs 的 UNUSED 里写明理由'
    : '逐条对应',
);
check(
  '白名单没有腐烂',
  staleAllowlist.length === 0,
  staleAllowlist.length > 0
    ? `这些码已经有落点了，理由已过期，请从白名单删掉：${staleAllowlist.join('、')}`
    : `名单 ${UNUSED.size} 条，全部仍无落点`,
);

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  "登记了却没人抛"的码此前只能靠人逐个回溯源码才发现，现在有机器兜底。');
console.log(`  它守的是"有没有落点"；"有没有断言"仍要人工 —— 见 docs/decisions/0060。`);
