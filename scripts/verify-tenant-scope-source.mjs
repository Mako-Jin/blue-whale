#!/usr/bin/env node
/**
 * 租户条件的取值来源自检 —— **SQL 里出现的租户值只能来自落点 / 认证上下文 / 实体自身**。
 *
 * ## 为什么需要它
 *
 * `0077` 的「刻意不查的」里留着一条，原话是：
 *
 * > **查询路径的租户条件从哪来。** 规则 C 判的是"比较"，规则 D 判的是"按主键取实体"；
 * > 而 `page` / `stats` 那类查询走的是 `.eq(Entity::getTenantId, <某个变量>)` ——
 * > 那个变量是不是来自落点，静态看不出来（要追局部变量的赋值）。……
 * > **这是那支脚本里目前最大的空白。**
 *
 * 那份判断里对了一半：追局部变量的赋值确实要做，而**做得出来**。`0076` 留下的判据
 * 在这里同样成立 —— **问"这东西是什么"能，问"它叫什么"不能**。这里要问的是
 * *"这个租户 id 是从哪儿取来的"*，而答案在源码里只有三个地方：
 *
 *     落点（TenantService）── resolveQueryTenant / resolveWriteTenant / currentReadTenant
 *     认证上下文 ─────────── AuthContextHolder，以及会话对象上的 getTenantId()
 *     实体自身 ───────────── @TableName 类型上的 getTenantId()；对象本身就是 bw_auth_tenant 时的 getId()
 *
 * 第四种来源是**请求绑定类型**（`*Controller` 方法形参上绑定的那些 DTO）——
 * 它正是这一族里唯一危险的那一种：传进来的租户 id 由调用方决定，而
 * "别家租户在系统里就是不存在"（`0025` 第 2 条）意味着它必须被忽略（查询）
 * 或拒绝（写入），两件事都只在落点里发生（`0074`）。
 *
 * ## 它查什么
 *
 * | # | 形状 | 判据 |
 * | --- | --- | --- |
 * | E1 | `.eq(<X>::getTenantId, ARG)` | `ARG` 的来源不得是请求绑定类型的对象 |
 * | E2 | SQL 文本里的 `#{tenantId}` | 它绑到的那个实参（或那个唯一的实体形参）来源同上 |
 *
 * 两条形状是同一句话的两个写法：**"租户条件"既可能写在 lambda wrapper 里，
 * 也可能写在 `@Select` / `@Insert` 的 SQL 文本里**。只守一条就是 `0077` 修过的那种不对称 ——
 * 规则 D 之所以存在，正是因为规则 A 看不见三条记录管道。
 *
 * `E2` 的判据落在**调用点**而不是 SQL 文本上：SQL 文本里没有"从哪来"这件事，只有调用点才有。
 * 例外是**唯一形参**那一种（`#{tenantId}` 从那个 bean 上读属性，`@Insert` 的列清单就是这样），
 * 它没有调用点可看，判据就是那个形参的类型是不是实体。
 *
 * ## 判据是**类型**，不是名字
 *
 * "请求绑定类型"这一集从源码推导：`*Controller` 里**带注解的形参**的声明类型。
 * 构造器形参没有注解，所以不会被收进来 —— 写本脚本时踩过：不筛注解的话
 * `AuditLogController(AuditLogService auditLogService)` 会把 `AuditLogService`
 * 也算成一个"请求绑定类型"，于是十几处正当写法当场变红。
 * 所以这里**没有任何白名单**，也没有一张"哪些类算 DTO"的对照表。
 *
 * ## 追不到就红
 *
 * 裸标识符的来源按一跳追：本方法内的局部初值 → 本方法形参 → 该形参的调用点实参。
 * 中间任何一步追不到（既不是局部、也不是形参；是形参但一个调用点都找不到），
 * 以及来源表达式认不出属于哪一类，**一律判红**（`0076` 的自检要求）。
 *
 * 声明与调用**不靠名字区分**：参数表的那些 `(` 位置是从"方法体之前的最后一个 `(`"
 * 与"注解 SQL 之后声明里的第一个 `(`"显式收出来的，收进 `declarationParens`，
 * 扫描时跳过它们。第一版用"参数表后面跟不跟 `{`"来判，在 **Mapper 接口**上失效
 * ——接口方法没有 `{`，于是它把自己的声明读成了一个调用点（`0077` 同款陷阱：
 * *"解析不出来必须红"* 的前提是"解析得出来"，而解析器的形状一旦不覆盖真实写法，
 * 报出来的就是一屏废话）。
 *
 * 这条设计还有一个好处：**落点改名不会造成假阴**。判据里写着 `resolveQueryTenant` 这些名字，
 * 一旦改了名，那几处会"认不出"从而变红，而不是静默离开射程 ——
 * 报错方向是"多报"而不是"漏报"。为了让那句红话说得清楚，另有一条自检确认
 * 那三个方法在 `TenantService` 里都还在。
 *
 * ## 用法
 *
 *     node scripts/verify-tenant-scope-source.mjs
 *
 * 零依赖、不连网、不编译、不读产物，失败时非零退出。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **`@Update … WHERE id = #{id}` 这类"按主键改"的注解 SQL 完全没有租户条件**。
 *   它是**规则 A / D 在写侧的镜像**，不是"租户条件从哪来"的一部分（那里压根没有租户值）。
 *   现状是安全的但只是一次手工核对：`restoreDeletedById` 的 `id` 来自
 *   `selectAnyByOrgAndUser`（按组织 + 用户查，而不是按主键），且调用方已先确认过归属。
 *   这条留给下一片。
 * - **`#{it.tenantId}` 这种写法**。它出现在 `@Insert` 的列清单里（`ClientLogMapper` /
 *   `ClientEventMapper`），取的是**待插入实体自己的租户** —— 那是"这行数据归谁"，
 *   由写侧落点 `resolveWriteTenant` 管，与"条件从哪来"不是一件事。射程只认裸 `#{tenantId}`。
 * - **`entity.setTenantId(...)` 写侧**。`0071` / `0074` 的那一半，与本脚本无关。
 * - **`.eq` 之外的条件形状**。全仓除 `eq` 外用 `getTenantId` 的写法一处都没有
 *   （`ne` / `in` / `like` / `orderBy` 全为空），所以只认 `eq` 不是漏，是形状本来就只有一个。
 *   代价：将来若有人写出 `.ne(UserEntity::getTenantId, x)`，它不在射程 ——
 *   但那种写法在本仓没有任何正当用途，一眼可见。
 * - **把 `Entity::getTenantId` 先存进变量再传**。`SFunction` 类型的局部变量是可能的写法，
 *   本仓一处都没有；它落在 `.eq(<X>::getTenantId, …)` 这个形状之外，因此看不见
 *   （`0072` 规则 6 登记过的同类假阴；与那支脚本里"先取局部变量再比"是同一种代价）。
 * - **跨模块的调用点**。形参的来源要在调用点上才看得见，而调用点的搜索射程是**同一个模块**。
 *   本仓的 8 个形参来源全在同模块内（`OrganizationMemberService` ← `DataScopeService` 等），
 *   所以够用；一个跨模块的同名方法会同时被当成两处，代价是误报而不是漏报。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SERVER = join(ROOT, 'server');

const problems = [];

function check(label, ok, detail) {
  console.log(`${ok ? '✓' : '✗'} ${label}`);
  if (detail) console.log(`    ${detail}`);
  if (!ok) problems.push(`${label}${detail ? ` —— ${detail}` : ''}`);
}

/* ------------------------------------------------------------------ 工具 */

const escapeRe = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** 去掉注释但**保留换行与列位**（要报 `行:列`，与 verify-tenant-visibility.mjs 同一段理由） */
function stripComments(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, (whole) => whole.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1');
}

/** 字符下标 → `行:列` */
function locate(text, index) {
  const head = text.slice(0, index);
  return `${head.split('\n').length}:${index - head.lastIndexOf('\n')}`;
}

/** 逐个冒泡到模块目录，收集 `server/<组>/<模块>/<子路径>` */
function moduleDirs(...sub) {
  const out = [];
  if (!existsSync(SERVER)) return out;
  for (const group of readdirSync(SERVER, { withFileTypes: true })) {
    if (!group.isDirectory()) continue;
    const groupPath = join(SERVER, group.name);
    for (const module of readdirSync(groupPath, { withFileTypes: true })) {
      if (!module.isDirectory()) continue;
      const full = join(groupPath, module.name, ...sub);
      if (existsSync(full)) out.push({ module: module.name, path: full });
    }
  }
  return out;
}

/**
 * 跳过一个字符串字面量：`"` / `'` 到**同种引号**，以及文本块 `"""` 到**下一个 `"""`**。
 *
 * 文本块这一支是本脚本必须单独处理的：注解 SQL 全部写成 `@Select("""…""")`，
 * 少了它会把 SQL 文本里每一个 `"` 都当成字符串边界，于是 `(` `)` 的配平从中间就开始错 ——
 * 第一版就是这样把 SQL 的中间一截当成了"方法声明"，`nameOf` 于是返回 `?`，
 * 报出来的每一句都是废话（见文件头对 `declarationParens` 的说明）。
 * 返回"下一个应继续扫描的下标"。
 */
function skipLiteral(text, index) {
  if (text.startsWith('"""', index)) {
    const close = text.indexOf('"""', index + 3);
    return close < 0 ? text.length : close + 3;
  }
  const quote = text[index];
  let i = index + 1;
  while (i < text.length && text[i] !== quote) {
    if (text[i] === '\\') i += 1;
    i += 1;
  }
  return i + 1;
}

/**
 * 按花括号深度切出类体里的每一个**带体的成员**（与 `verify-tenant-visibility.mjs` 同一份实现，
 * 多返回一个 `bodyEnd`）。深度约定：0 = 文件顶层，1 = 类的体，2 = 方法的体，
 * 于是"深度 1 上遇到的 `{`"就是方法体的开始 —— `while (...)` / `switch (...)` 这类
 * **语句**的块在深度 2 以上，出不来签名。只收"签名以 `)` 结尾"的，
 * 字段声明与内部类因此被排除。
 */
function membersOf(text) {
  const found = [];
  let depth = 0;
  let boundary = 0;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '"' || ch === "'") {
      i = skipLiteral(text, i) - 1;
      continue;
    }
    if (ch === '{') {
      if (depth === 1) {
        found.push({
          signature: text.slice(boundary, i).replace(/\s+/g, ' ').trim(),
          start: boundary,
          bodyStart: i,
          bodyEnd: null,
          body: null,
        });
      }
      depth += 1;
    } else if (ch === '}') {
      depth -= 1;
      const last = found[found.length - 1];
      if (depth === 1 && last && last.bodyEnd === null) {
        last.bodyEnd = i;
        last.body = text.slice(last.bodyStart + 1, i);
      }
      if (depth <= 1) boundary = i + 1;
    } else if (ch === ';' && depth === 1) {
      boundary = i + 1;
    }
  }
  return found.filter(
    (item) => item.body !== null && /\)\s*(throws\s+[\w.,\s]+)?$/.test(item.signature),
  );
}

/**
 * 签名里**参数表**那个 `(` 的位置。
 *
 * 判据是"配平终点之后只剩空白或 `throws …`"—— 也就是"括号内容一直走到末尾"。
 * 不能取第一个、也不能取最后一个：`membersOf` 交出来的签名**连前置注解一起**，
 * 于是第一个 `(` 属于 `@Transactional(readOnly = true)`；而形参各自带着
 * `@Param("…")`，最后一个 `(` 又属于最后一个注解（两种都踩过：
 * 前者把方法名读成 `Transactional`，后者读成 `Param`）。
 */
function paramParenIn(signature) {
  for (let i = 0; i < signature.length; i += 1) {
    if (signature[i] !== '(') continue;
    const balanced = balancedOf(signature, i);
    if (/^\s*(?:throws\s+[\w.,\s]+)?$/.test(signature.slice(balanced.end + 1))) return i;
  }
  return -1;
}

/** 签名里参数表之前最后一个标识符 —— 方法名 */
function nameOf(signature) {
  const at = paramParenIn(signature);
  if (at < 0) return '?';
  const matched = /([A-Za-z_]\w*)\s*$/.exec(signature.slice(0, at));
  return matched ? matched[1] : '?';
}

/** 一段文本末尾的标识符（注解 SQL 之后的声明用它取方法名） */
function nameBefore(text) {
  const matched = /([A-Za-z_]\w*)\s*$/.exec(text);
  return matched ? matched[1] : '?';
}

/**
 * 一个**带体的成员**参数表那个 `(` 的绝对位置 —— 与 `paramParenIn` 同一条判据，
 * 只是换到未折叠的原文上按绝对下标算，好让调用点的位置能与声明对齐。
 */
function paramParenOf(text, member) {
  for (let i = member.start; i < member.bodyStart; i += 1) {
    if (text[i] !== '(') continue;
    const balanced = balancedOf(text, i);
    if (balanced.end > member.bodyStart) continue;
    if (/^\s*(?:throws\s+[\w.,\s]+)?$/.test(text.slice(balanced.end + 1, member.bodyStart))) {
      return i;
    }
  }
  return -1;
}

/** `open`（指向某个 `(`）之后那个**配平**的括号内容，连结束位置一起返回 */
function balancedOf(text, open) {
  let depth = 0;
  for (let i = open; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '"' || ch === "'") {
      i = skipLiteral(text, i) - 1;
      continue;
    }
    if (ch === '(') depth += 1;
    else if (ch === ')') {
      depth -= 1;
      if (depth === 0) return { text: text.slice(open + 1, i), end: i };
    }
  }
  return { text: text.slice(open + 1), end: text.length };
}

/** 按**顶层逗号**切开 —— 注解里的括号（`@Parameter(description = "…")`）因此不会被切碎 */
function splitArgs(text) {
  const parts = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '(' || ch === '<') depth += 1;
    else if (ch === ')' || ch === '>') depth -= 1;
    else if (ch === ',' && depth <= 0) {
      parts.push(text.slice(start, i));
      start = i + 1;
    }
  }
  parts.push(text.slice(start));
  return parts.map((part) => part.trim()).filter((part) => part.length > 0);
}

/** 剥掉形参声明开头的注解，返回 `{ annotated, type, name }` */
function parseParam(raw) {
  const annotated = /^\s*@/.test(raw);
  const stripped = raw.replace(/^\s*(?:@[\w.]+(?:\s*\([^)]*\))?\s*)+/, '').trim();
  const matched = /([\w$<>\[\].,?]+)\s+(\w+)$/.exec(stripped);
  return {
    annotated,
    type: (matched ? matched[1] : stripped).replace(/<.*/, ''),
    name: matched ? matched[2] : stripped,
  };
}

/** 一个成员签名里的形参表 */
function paramsOf(signature) {
  const at = paramParenIn(signature);
  if (at < 0) return [];
  return splitArgs(signature.slice(at + 1, balancedOf(signature, at).end)).map(parseParam);
}

/* --------------------------------------------------- 一、源码：三层映射 */

const javaFiles = [];
for (const { module, path } of moduleDirs('src', 'main', 'java')) {
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.java')) javaFiles.push({ module, file: full });
    }
  };
  walk(path);
}

const sources = javaFiles.map(({ module, file }) => {
  const text = stripComments(readFileSync(file, 'utf8'));
  const declared = /\b(?:class|interface|record|enum)\s+(\w+)/.exec(text);
  return {
    module,
    shown: relative(ROOT, file).replace(/\\/g, '/'),
    text,
    type: declared ? declared[1] : null,
    members: membersOf(text),
  };
});
const byShown = new Map(sources.map((source) => [source.shown, source]));

/** `@TableName("…")` 的类型 → 表名（"这东西是不是实体""它是不是那张租户表"都问它） */
const tableOfEntity = new Map();
for (const { text } of sources) {
  for (const matched of text.matchAll(/@TableName\s*\(\s*"([^"]+)"\s*\)/g)) {
    const declared = /(?:class|record)\s+(\w+)/.exec(text.slice(matched.index));
    if (declared) tableOfEntity.set(declared[1], matched[1]);
  }
}

/**
 * **请求绑定类型** —— `*Controller` 里带注解的形参的声明类型。
 * 它只用来判红，所以"多收一个"的代价是误报、"少收一个"的代价是假阴；
 * 判据取注解而不是取名字，是为了不写任何白名单（见文件头）。
 */
const boundTypes = new Set();
for (const source of sources) {
  if (!/\w+Controller\.java$/.test(source.shown)) continue;
  for (const member of source.members) {
    for (const param of paramsOf(member.signature)) {
      if (param.annotated && param.type) boundTypes.add(param.type);
    }
  }
}

if (tableOfEntity.size === 0 || boundTypes.size === 0) {
  console.log(
    `✗ 映射链没解析出来（@TableName ${tableOfEntity.size} 个 · 请求绑定类型 ${boundTypes.size} 个）` +
      ' —— 形状变了的话，本脚本会退化成一条永远为真的空断言',
  );
  process.exit(1);
}

/* ------------------------------------------------- 二、一跳：来源解析链 */

/** 方法参数表那些 `(` 的**绝对位置** —— 它们不是调用点 */
const declarationParens = new Set();
for (const source of sources) {
  for (const member of source.members) {
    const at = paramParenOf(source.text, member);
    if (at >= 0) declarationParens.add(at);
  }
}

/** 某个下标落在哪个成员里 */
function memberAt(shown, index) {
  const source = byShown.get(shown);
  if (!source) return null;
  return (
    source.members
      .filter((member) => index >= member.start && index <= member.bodyEnd)
      .sort((a, b) => a.start - b.start)
      .pop() ?? null
  );
}

/** 标识符的声明类型：本方法形参 → 本方法局部 → 本文件（字段 / 别处） */
function typeOfIdent(shown, member, name) {
  const source = byShown.get(shown);
  if (!source) return '';
  if (member) {
    for (const param of paramsOf(member.signature)) if (param.name === name) return param.type;
    const local = new RegExp(`\\b([\\w$<>\\[\\].,?]+)\\s+${name}\\s*[=;]`).exec(member.body ?? '');
    if (local) return local[1].replace(/<.*/, '');
  }
  const anywhere = new RegExp(`\\b([\\w$<>\\[\\].,?]+)\\s+${name}\\s*[=;]`).exec(source.text);
  return anywhere ? anywhere[1].replace(/<.*/, '') : '';
}

/**
 * 一个方法的调用点实参（按形参位置取）。
 *
 * 先看**同文件里的自调用**（私有方法只可能这么被调，含 `this.`）；
 * 不是私有的话，再看**本模块里带接收者的调用**：接收者的名字从
 * "哪个类声明了这个类型的字段/参数"读出来，所以
 * `organizationMemberService.primaryOrganizationOf(…)` 认得出，
 * 而与它同名的别的方法不会被当成它的调用点。
 *
 * 参数表那些 `(` 由 `declarationParens` 直接跳过 —— 不靠"后面跟不跟 `{`"猜。
 */
function callArgumentsOf(source, methodName, position, isPrivate) {
  const hits = [];
  const seen = new Set();
  const take = (peer, at) => {
    const open = at + methodName.length;
    if (declarationParens.has(open) || seen.has(`${peer.shown}#${open}`)) return;
    seen.add(`${peer.shown}#${open}`);
    const args = splitArgs(balancedOf(peer.text, open).text);
    if (args[position]) {
      hits.push({ shown: peer.shown, text: peer.text, index: open, arg: args[position] });
    }
  };
  if (isPrivate) {
    const self = new RegExp(`(?<![\\w.])(?:this\\s*\\.\\s*)?${escapeRe(methodName)}\\s*\\(`, 'g');
    for (const matched of source.text.matchAll(self)) {
      take(source, matched.index + matched[0].lastIndexOf(methodName));
    }
    return hits;
  }
  const scope = sources.filter((item) => item.module === source.module);
  const receivers = new Set();
  if (source.type) {
    for (const peer of scope) {
      for (const matched of peer.text.matchAll(
        new RegExp(`\\b${escapeRe(source.type)}\\s+([a-z]\\w*)\\s*[,;)=]`, 'g'),
      )) {
        receivers.add(matched[1]);
      }
    }
  }
  const names = [null, ...receivers];
  for (const peer of scope) {
    for (const receiver of names) {
      const pattern = receiver
        ? new RegExp(`\\b${escapeRe(receiver)}\\s*\\.\\s*${escapeRe(methodName)}\\s*\\(`, 'g')
        : new RegExp(`(?<![\\w.])${escapeRe(methodName)}\\s*\\(`, 'g');
      for (const matched of peer.text.matchAll(pattern)) {
        take(peer, matched.index + matched[0].lastIndexOf(methodName));
      }
    }
  }
  return hits;
}

const LANDING = /\.\s*(?:resolveQueryTenant|resolveWriteTenant|currentReadTenant)\s*\(/;

/** 一个来源表达式属于哪一类；返回 `kind: null` 表示**认不出**（调用方判红） */
function classifySource(shown, member, expr, depth = 0) {
  const text = expr.trim();
  if (depth > 4) return { kind: null, why: `来源嵌得太深：${text.slice(0, 60)}` };
  if (LANDING.test(text)) return { kind: '落点', why: `落点调用：${text.split('\n')[0]}` };
  if (/AuthContextHolder\s*\./.test(text)) return { kind: '上下文', why: '认证上下文' };
  const getter = /^(\w+)\s*\.\s*(\w+)\s*\(\s*\)$/.exec(text);
  if (getter) {
    const owner = typeOfIdent(shown, member, getter[1]);
    if (boundTypes.has(owner)) {
      return { kind: null, why: `读了请求绑定类型 ${owner} 上的租户：${getter[1]}.${getter[2]}()` };
    }
    if (/tenant/i.test(getter[2])) {
      return {
        kind: '实体自身',
        why: `非请求绑定类型（${owner || '未知'}）上的 ${getter[1]}.${getter[2]}()`,
      };
    }
    if (tableOfEntity.get(owner) === 'bw_auth_tenant') {
      return { kind: '实体自身', why: `对象本身就是租户实体（${owner}）` };
    }
    return { kind: null, why: `认不出这是租户来源：${text}` };
  }
  if (/^\w+$/.test(text)) {
    if (!member) return { kind: null, why: `不知道 ${text} 在哪个方法里，追不到它的来源` };
    const local = new RegExp(`\\b(?:final\\s+)?[\\w$<>\\[\\].,?]+\\s+${text}\\s*=\\s*([^;]+);`).exec(
      member.body ?? '',
    );
    if (local) return classifySource(shown, member, local[1], depth + 1);
    const position = paramsOf(member.signature).findIndex((param) => param.name === text);
    if (position < 0) {
      return { kind: null, why: `裸标识符 ${text} 既不是本方法的局部、也不是它的形参` };
    }
    const source = byShown.get(shown);
    const methodName = nameOf(member.signature);
    const hits = source
      ? callArgumentsOf(source, methodName, position, /\bprivate\b/.test(member.signature))
      : [];
    if (hits.length === 0) {
      return { kind: null, why: `形参 ${text} 在本模块里找不到调用点（追不到来源）` };
    }
    const bad = [];
    const kinds = new Set();
    for (const hit of hits) {
      const inner = classifySource(hit.shown, memberAt(hit.shown, hit.index), hit.arg, depth + 1);
      if (inner.kind) kinds.add(inner.kind);
      else bad.push(`${hit.shown}:${locate(hit.text, hit.index)} 传了「${hit.arg}」（${inner.why}）`);
    }
    if (bad.length > 0) {
      return {
        kind: null,
        why: `形参 ${text} 有调用点传入了非法的租户值：\n        ${bad.join('\n        ')}`,
      };
    }
    return {
      kind: [...kinds].sort().join(' + ') || '落点',
      why: `形参 ${text}（${hits.length} 个调用点全合规）`,
    };
  }
  return { kind: null, why: `认不出的来源表达式：${text.slice(0, 60)}` };
}

/* ------------------------------------------------------ 三、射程：两种形状 */

const invalid = []; // 每一句都能直接读
const sites = []; // 供人核对

/* E1 —— `.eq(<X>::getTenantId, ARG)` */
for (const source of sources) {
  for (const matched of source.text.matchAll(/\.eq\s*\(/g)) {
    const open = matched.index + matched[0].lastIndexOf('(');
    const args = splitArgs(balancedOf(source.text, open).text);
    if (args.length < 2) continue;
    if (!/^\w+\s*::\s*getTenantId$/.test(args[0])) continue;
    const member = memberAt(source.shown, matched.index);
    const where = `${source.shown}:${locate(source.text, matched.index)}`;
    const verdict = classifySource(source.shown, member, args[1]);
    if (!verdict.kind) {
      invalid.push(`${where} · ${member ? `${nameOf(member.signature)}()` : '?'} —— ${verdict.why}`);
    }
    sites.push({ where, shape: '.eq', kind: verdict.kind ?? '✗', why: verdict.why });
  }
}

/* E2 —— SQL 文本里的 `#{tenantId}` */
const sqlDeclarations = [];
for (const source of sources) {
  for (const annotated of source.text.matchAll(/@(?:Select|Insert|Update|Delete)\s*\(/g)) {
    const open = annotated.index + annotated[0].lastIndexOf('(');
    const sql = balancedOf(source.text, open);
    if (!/#\{\s*tenantId\s*\}/.test(sql.text)) continue;
    const semi = source.text.indexOf(';', sql.end);
    if (semi < 0) continue;
    const declaration = source.text.slice(sql.end, semi + 1);
    const offset = declaration.indexOf('(');
    if (offset < 0) continue;
    declarationParens.add(sql.end + offset);
    sqlDeclarations.push({
      source,
      params: splitArgs(balancedOf(declaration, offset).text).map(parseParam),
      name: nameBefore(declaration.slice(0, offset)),
      annotated: /@Param\s*\(\s*"tenantId"\s*\)/.test(declaration),
      where: `${source.shown}:${locate(source.text, open)}`,
    });
  }
}

for (const site of sqlDeclarations) {
  const dotted = `${site.where} · ${site.name}()`;
  if (!site.annotated) {
    // `#{tenantId}` 从**唯一形参**那个 bean 上读属性（`@Insert` 的列清单就是这样）
    if (site.params.length !== 1) {
      const why = `SQL 里的 #{tenantId} 既没有 @Param("tenantId")、形参又不是唯一一个（${site.params.length} 个），绑到谁身上认不出来`;
      invalid.push(`${dotted} —— ${why}`);
      sites.push({ where: site.where, shape: '#{tenantId}', kind: '✗', why });
      continue;
    }
    const bean = site.params[0];
    if (tableOfEntity.has(bean.type)) {
      sites.push({
        where: site.where,
        shape: '#{tenantId}',
        kind: '实体自身',
        why: `唯一形参 ${bean.name}（${bean.type}）是实体 ⇒ 读的是这一行自己的租户`,
      });
    } else {
      const why = `唯一形参 ${bean.name} 的类型 ${bean.type} 不是实体 —— #{tenantId} 会从它身上读成什么说不清`;
      invalid.push(`${dotted} —— ${why}`);
      sites.push({ where: site.where, shape: '#{tenantId}', kind: '✗', why });
    }
    continue;
  }
  const position = site.params.findIndex((param) => param.name === 'tenantId');
  if (position < 0) {
    const why = '@Param("tenantId") 标在一个不叫 tenantId 的形参上';
    invalid.push(`${dotted} —— ${why}`);
    sites.push({ where: site.where, shape: '#{tenantId}', kind: '✗', why });
    continue;
  }
  const hits = callArgumentsOf(site.source, site.name, position, false);
  if (hits.length === 0) {
    const why = `找不到调用点 —— #{tenantId} 的取值追不到（${site.params.map((p) => `${p.type} ${p.name}`).join(', ')}）`;
    invalid.push(`${dotted} —— ${why}`);
    sites.push({ where: site.where, shape: '#{tenantId}', kind: '✗', why });
    continue;
  }
  const bad = [];
  const kinds = new Set();
  for (const hit of hits) {
    const inner = classifySource(hit.shown, memberAt(hit.shown, hit.index), hit.arg);
    if (inner.kind) kinds.add(inner.kind);
    else bad.push(`${hit.shown}:${locate(hit.text, hit.index)} 传了「${hit.arg}」（${inner.why}）`);
  }
  if (bad.length > 0) {
    const why = `#{tenantId} 有调用点传入了非法的租户值：\n        ${bad.join('\n        ')}`;
    invalid.push(`${dotted} —— ${why}`);
    sites.push({ where: site.where, shape: '#{tenantId}', kind: '✗', why });
  } else {
    sites.push({
      where: site.where,
      shape: '#{tenantId}',
      kind: [...kinds].sort().join(' + ') || '落点',
      why: `${site.name}()（${hits.length} 个调用点全合规）`,
    });
  }
}

/* -------------------------------------------------------------------- 自检 */

const LANDING_OWNER = '/auth/tenant/TenantService.java';
const landingClass = sources.find(({ shown }) => shown.endsWith(LANDING_OWNER));
const landingNames = ['resolveQueryTenant', 'resolveWriteTenant', 'currentReadTenant'];
check(
  '三个查询落点在租户服务里都还在（改了名不会静默离开射程，只会变红）',
  Boolean(landingClass) &&
    landingNames.every((name) => new RegExp(`\\b${name}\\s*\\(`).test(landingClass.text)),
  landingClass ? landingNames.join(' · ') : `找不到 ${LANDING_OWNER}`,
);

const eqSites = sites.filter((site) => site.shape === '.eq');
const sqlSites = sites.filter((site) => site.shape === '#{tenantId}');
check(
  `射程非空（.eq ${eqSites.length} 处 · SQL 里的 #{tenantId} ${sqlSites.length} 个方法 · 请求绑定类型 ${boundTypes.size} 个）`,
  eqSites.length > 0 && sqlSites.length > 0 && boundTypes.size > 0,
  '射程为空 = 一条永远为真的空断言',
);

check(
  'SQL 里的租户值只来自落点 / 认证上下文 / 实体自身',
  invalid.length === 0,
  invalid.length > 0
    ? `${invalid.length} 处：\n    ${invalid.join('\n    ')}`
    : `${sites.length} 处全部可推导 —— 读的是"请求绑定类型"上的租户才算越权`,
);

/* --------------------------------------------------------------- 供人核对 */

console.log('');
const byKind = new Map();
for (const site of sites) byKind.set(site.kind, (byKind.get(site.kind) ?? 0) + 1);
console.log('  取值来源分布：');
for (const [kind, count] of [...byKind.entries()].sort((a, b) => b[1] - a[1])) {
  console.log(`    ${kind} —— ${count} 处`);
}
console.log(
  `  请求绑定类型（只用来判红，从 *Controller 的带注解形参推导）：${boundTypes.size} 个`,
);
console.log(`    ${[...boundTypes].sort().join(' · ')}`);
console.log('');

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  "查询路径的租户条件从哪来"此前是 0077「刻意不查的」里最大的一条 ——');
console.log('  它要的"追局部变量的赋值"做得出来：来源只有落点 / 认证上下文 / 实体自身三类，');
console.log('  第四类（请求绑定类型）是唯一危险的那一种，判据取"类型"所以不需要白名单。');
console.log('  未覆盖：@Update … WHERE id = #{id} 这类"按主键改"的注解 SQL（写侧镜像，见文件头）。');
