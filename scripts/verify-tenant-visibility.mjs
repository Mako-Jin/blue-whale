#!/usr/bin/env node
/**
 * 租户可见性自检 —— **按主键取实体的路径必须顺手回答"这位操作者看得见它吗"**。
 *
 * ## 为什么需要它
 *
 * `0025` 第 2 条立过一条：**别家租户的对象在系统里就是不存在**，所以"看不见"必须与
 * "不存在"给同一个答案。`0074` 把它落成 `TenantService#assertVisible`，
 * 并在用户 / 组织 / 角色 / 用户组四域各写了一句 —— 但那是**一次手工核对**。
 * `0074` 的「未覆盖 4」自己写着这句话：*"角色 / 用户组 / 资源三域的引用列都已有
 * 同租户或平台级校验，但这是一次手工核对，**没有守卫**。"*
 *
 * 这个缺口有代价，而且 `0075` 已经付过一次：那张自称权威的 `40002` 文案清单，
 * `0074` 新加的两处**一次都没登记进来**，直到 `0075` 逐个回溯源码才对齐。
 * 清单会漂，因为没有任何东西拦着它漂。
 *
 * `0075` 曾把这件事写进「刻意不查的」，理由是**"表名 → 类名不是无歧义的 token 形状"**
 * （`bw_auth_user` → `UserService#requireUser`、`bw_auth_organization` → `requireOrg`……），
 * 只能硬编码对照表。**那个理由推错了方向**：需要的不是"表名 → 方法名"，
 * 而是"**这个 `selectById` 取的是哪张表的行**"，而这条链的每一环都逐字写在源码里：
 *
 *     字段名 ──(private final XxxMapper xxxMapper;)──▶ Mapper 接口
 *            ──(extends BaseMapper<XxxEntity>)─────▶ 实体
 *            ──(@TableName("bw_xxx"))──────────────▶ 表名
 *            ──(建表 / 加列脚本)────────────────────▶ tenant_id 可空吗
 *
 * 四步都是**声明里的字面量**，没有一步需要猜。所以这里没有任何对照表。
 *
 * `0077` 又补上了这条链**够不到的那一半**：审计 / 日志 / 事件三条记录管道用的是公共 `40400`
 * （它们没有自己的枚举），于是规则 A 从头到尾看不见它们 —— 而"平台级操作者也读不到别家的记录"
 * 这句口径，此前只以两条用例的断言形式存在（而且用例从没说过自己的身份是平台级）。
 * 规则 D 就是规则 A 在它们身上的镜像。
 *
 * ## 它查什么
 *
 * | # | 规则 | 为什么它是错 |
 * | --- | --- | --- |
 * | A | 同一方法里，对一张 `tenant_id NOT NULL` 的表 `selectById` **并且**抛出**该表对应的那一个** `404xx` ⇒ 方法里必须调 `assertVisible(` | 这时这个方法就是"这个对象的读侧出口"，少了判定，跨租户的 id 会得到一个"安静但错误"的答案（`0025` 第 2 条） |
 * | A2 | 那个 `assertVisible` 传的码必须与方法里抛的码**逐字相同** | 不同码 = 两条分支说两件事；同一个 `requireXxx` 里"不存在"与"看不见"是同一个对象，只可能有一个码（`0059` · `0075`） |
 * | B | 三方言里每张表的 `tenant_id` **存在性与可空性必须一致** | 它是规则 A / D 的射程判据。而 **dameng 是唯一没被 CI 真跑过的方言** —— 它的口径分裂没有任何测试能拦 |
 * | C | **接上了租户服务的模块**里，`main` 不得让 `getTenantId()` 参与相等或不等比较 | 那是在落点之外手搓同租户判定 —— `0075`「未覆盖 2」登记过的那件事 |
 * | D | 抛出**公共** `40400` 的方法（含调用同文件里那个构造器的）若按主键取实体 ⇒ 必须走**记录管道的落点** | 这是规则 A 在三条记录管道上的镜像：它们没有自己的码（用的是公共 `40400`），所以规则 A 看不见它们。少了这一步，"平台级也读不到别家"就只是签名上的巧合（`0077`） |
 *
 * 规则 C / D 的射程与判据也都是推导的：
 *
 * - **"接上了租户服务"** = 模块里出现了 `TenantService` 类型的字段/参数，并真的调了它的方法。
 *   这句话曾经写作"模块内出现过那两个落点的调用"（`0076`），本片**放宽**成"用过租户服务"，
 *   因为在 {@code TenantService} 已经摆在手边的模块里手搓一份租户比较，无论如何都是第四个口径；
 *   而放宽之后三条记录管道才进得了射程 —— 它们此前调的是自己的私有方法，两个落点名一个都不沾。
 * - **"记录管道的落点"** 不写名字：它是 `TenantService` 里**方法体提到公共 `ResultCode.NOT_FOUND`**
 *   的那一个（`0077` 把它落成 `assertRecordInCurrentTenant`）。解析不出来就红 —— 否则规则 D
 *   会退化成一条永远为真的空断言。
 * - 规则 C 的"不得手搓"只在**一个租户模型**下才成立 —— 审计域就曾有自己的一套
 *   （`AuditLogService#detail` 拿 `Objects.equals(entity.getTenantId(), …)` 判归属，
 *   它**故意**不接受 `null`：未登录时产生的审计行不该对任何人可见）。
 *   `0077` 之后这一族也走落点了，所以本规则与规则 D 现在合起来说的是同一件事：
 *   **记录管道的租户判定只有一处可写**。
 *
 * ## 用法
 *
 *     node scripts/verify-tenant-visibility.mjs
 *
 * 零依赖、不连网、不编译、不读产物，失败时非零退出。
 *
 * ## 射程是**推导**出来的，不是列出来的
 *
 * 规则 A 只对"表带 `tenant_id NOT NULL`"生效，于是两处天然出局，**不需要白名单**：
 *
 * - `bw_auth_resource` 的 `tenant_id` 是可空的（平台级资源恒为 `NULL`）——
 *   对它的实体调 `assertVisible` 是**空操作**（那条判定遇到 `null` 直接返回）；
 * - `bw_auth_tenant` **根本没有 `tenant_id` 列** —— 它本身是租户，"租户归属"就是它自己，
 *   可见性由 `TenantService#requireVisibleTenant` 单独说。
 *
 * 而反过来，**`tenant_id` 可空**的三张表（`bw_audit_log` / `bw_client_log` / `bw_client_event`）
 * 恰恰是规则 D 的射程：它们的 `null` 不是"平台级对象"而是"不属于任何租户"，
 * 与 `bw_auth_resource` 是**相反**的两件事 —— 这就是那一族不能共用 `assertVisible` 的原因（`0077`）。
 *
 * 这两条理由都从 DDL 读出来，写进输出里；哪天门数变了，输出会变。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **同一方法里 `selectById` 与"另一个对象"的 `404xx` 同时出现**。
 *   实例如 `OrganizationMemberService#setPrimaryPosition`：它取用户（用于回填展示列）
 *   却抛归属的 `40410` —— 那个 404 的理由**不覆盖**这次取数，所以不在射程。
 *   判它就得理解语义。脚本把这类位置**印出来**（见输出的"另有 N 处"），
 *   而**语义写在那一处的源码注释里**，读的人从输出能直接落到答案上。
 * - **"该不该做同租户判定"这件事本身**。规则 C 只覆盖"已经在比、却绕开落点"的写法；
 *   `Objects.equals` 里传的是**局部变量**（先 `Long t = x.getTenantId()` 再比）它看不到。
 *   手搓的写法无穷（`0075`「未覆盖 2」），这里守的是最常见的几种，**不是全部**。
 * - **查询路径的租户条件从哪来**。规则 C 判的是"比较"，规则 D 判的是"按主键取实体"；
 *   而 `page` / `stats` 那类查询走的是 `.eq(Entity::getTenantId, <某个变量>)` ——
 *   那个变量是不是来自落点，静态看不出来（要追局部变量的赋值）。
 *   `0077` 把这件事收到的落点只有一个，所以**改动它需要动一处**；
 *   但"下次有人再手写一遍"没有任何机器拦着，这是本脚本目前最大的空白。
 * - **枚举常量改名带来的射程漂移**。规则 A 的判据是"源码里抛的那个码 == 从表名推出的那个码"，
 *   **两侧都不读枚举**（所以重命名枚举里那句话的名字不影响射程的语义）。代价是：**同时**改枚举
 *   常量名与它的全部源码引用（保持可编译）会让那一处按新名字重新推导不出来，从而**静默离开射程**。
 *   没把这条做成检查，是因为"这张表被 `selectById` 取过、却没有抛出它对应的那个码"**本来就有
 *   正当情形**（`setPrimaryPosition` 取用户、抛的是归属的 `40410`），判它得理解语义；
 *   而真正会发生的改名是**跨文件的整体重命名**（`AuthResultCode` + 二十来处引用 + 用例 +
 *   `server/README.md` 那张权威清单），那种改法会撞上下面的"射程为空 ⇒ 红"。**兜底就是那一条。**
 * - **`tenant_id` 之外的列**。三方言只对 `tenant_id` 对账 —— 把"三方言 DDL 全等"做成守卫
 *   会误报（类型名本来就不同：`BIGINT` / `NUMBER`），而**会误报的守卫比没有更糟**（`0064`）。
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

/**
 * 去掉注释，**但保留换行与列位**（注释里的每个非换行字符换成空格）。
 *
 * 与 `verify-notfound-messages.mjs` 同一条理由（源码里到处是
 * `{@code AuthResultCode.USER_NOT_FOUND}` 这类**说明**，不剥掉的话一句注释就能
 * 把违规写法伪装成合规），但多一条要求：本脚本要报 `行:列`，
 * 所以不能像那份实现那样把整段注释连同换行一起吃掉。
 */
function stripComments(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, (whole) => whole.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1');
}

/** 把字符下标换算成 `行:列`，让报告直接能点开 */
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
 * 按花括号深度切出类体里的每一个**带体的成员**。
 *
 * 深度约定：0 = 文件顶层，1 = 类的体，2 = 方法的体。于是"深度 1 上遇到的 `{`"
 * 就是一个方法体的开始，它之前（截至上一个深度 1 的边界）的文本就是签名。
 * 这样 `while (...)` / `switch (...)` 这类**语句**的括号块不会被误认成方法 ——
 * 它们是深度 2 以上，出不来签名。
 *
 * 只收"签名以 `)` 结尾（可选跟 `throws …`）"的那些：字段声明、
 * 内部类声明、静态初始化块都因此被排除，用不着另写白名单。
 */
function membersOf(text) {
  const found = [];
  let depth = 0;
  let boundary = 0;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '"' || ch === "'") {
      const quote = ch;
      i += 1;
      while (i < text.length && text[i] !== quote) {
        if (text[i] === '\\') i += 1;
        i += 1;
      }
      continue;
    }
    if (ch === '{') {
      if (depth === 1) {
        const signature = text.slice(boundary, i).replace(/\s+/g, ' ').trim();
        found.push({
          signature,
          isMethod: /\)\s*(throws\s+[\w.,\s]+)?$/.test(signature),
          body: null,
          start: boundary,
          bodyStart: i,
        });
      }
      depth += 1;
    } else if (ch === '}') {
      depth -= 1;
      const last = found[found.length - 1];
      if (depth === 1 && last && last.body === null) last.body = text.slice(last.bodyStart + 1, i);
      if (depth <= 1) boundary = i + 1;
    } else if (ch === ';' && depth === 1) {
      boundary = i + 1;
    }
  }
  return found.filter((item) => item.isMethod && item.body !== null);
}

/** 签名里 `(` 之前最后一个标识符 —— 方法名 */
function nameOf(signature) {
  const beforeParen = signature.slice(0, signature.lastIndexOf('('));
  const matched = /([A-Za-z_]\w*)\s*$/.exec(beforeParen);
  return matched ? matched[1] : '?';
}

/** 取出 `open`（指向某个 `(`）之后那个**配平**的实参文本 */
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

const sources = javaFiles.map(({ module, file }) => ({
  module,
  shown: relative(ROOT, file).replace(/\\/g, '/'),
  text: stripComments(readFileSync(file, 'utf8')),
}));

/**
 * "接上了租户服务"的模块（规则 C 的射程）。
 *
 * <p>判据**不写落点的名字**：先看这个文件有没有 `TenantService` 类型的字段/参数
 * （名字从声明里读，叫 `tenantService` 还是 `tenants` 都行），再看那个名字有没有被真的调用。
 * 理由见文件头：在 `TenantService` 已经摆在手边的模块里手搓一份租户比较，无论如何都是第四个口径。
 */
const TENANT_SERVICE_OWNER = '/auth/tenant/TenantService.java';
const adopters = new Set();
const landingClass = sources.find(({ shown }) => shown.endsWith(TENANT_SERVICE_OWNER));

for (const { module, text } of sources) {
  const receivers = [...new Set([...text.matchAll(/\bTenantService\s+(\w+)\s*[,);=]/g)].map((m) => m[1]))];
  if (receivers.some((name) => new RegExp(`\\b${name}\\s*\\.\\s*\\w+\\s*\\(`).test(text))) {
    adopters.add(module);
  }
}

if (!landingClass) {
  console.log(`✗ 找不到租户服务：${TENANT_SERVICE_OWNER} —— 落点搬了家，规则 C / D 会静默失效`);
  process.exit(1);
}

/**
 * **记录管道的落点**清单 —— 从声明里推导，不是写死名字：
 * `TenantService` 里**方法体提到公共 `ResultCode.NOT_FOUND`** 的那一个（们）。
 *
 * <p>为什么这样认：三条记录管道没有自己的枚举，用的是公共 `40400`（`0059`）；
 * 所以"这个落点抛的是哪个码"这件事，在源码里就是它的方法体。推不出来就红 ——
 * 否则规则 D 会退化成一条永远为真的空断言。
 */
const recordLandings = landingClass.text
  ? membersOf(landingClass.text)
      .filter((member) => /ResultCode\s*\.\s*NOT_FOUND/.test(member.body ?? ''))
      .map((member) => nameOf(member.signature))
  : [];

const tableOfEntity = new Map(); // UserEntity -> bw_auth_user
const entityOfMapper = new Map(); // UserMapper -> UserEntity
const typeOfField = new Map(); // UserService#userMapper -> UserMapper

for (const { text } of sources) {
  for (const matched of text.matchAll(/@TableName\s*\(\s*"([^"]+)"\s*\)/g)) {
    const declared = /(?:class|record)\s+(\w+)/.exec(text.slice(matched.index));
    if (declared) tableOfEntity.set(declared[1], matched[1]);
  }
  for (const matched of text.matchAll(/interface\s+(\w+)\s+extends\s+BaseMapper\s*<\s*(\w+)\s*>/g)) {
    entityOfMapper.set(matched[1], matched[2]);
  }
}

for (const { text } of sources) {
  const owner = /class\s+(\w+)/.exec(text);
  if (!owner) continue;
  for (const matched of text.matchAll(
    /(?:private|protected|public)?\s*(?:final\s+)?(\w+Mapper)\s+(\w+)\s*;/g,
  )) {
    typeOfField.set(`${owner[1]}#${matched[2]}`, matched[1]);
  }
}

if (tableOfEntity.size === 0 || entityOfMapper.size === 0) {
  console.log('✗ 一个 `@TableName` / `BaseMapper<…>` 都没解析出来 —— 映射链的形状变了？');
  process.exit(1);
}

if (recordLandings.length === 0) {
  console.log('✗ 在租户服务里找不到"抛公共 40400"的那个方法 —— 记录管道的落点认不出来了？');
  process.exit(1);
}

/** 表名 → 那张表对应的 `404xx` 常量名（`bw_auth_user_group` → `USER_GROUP_NOT_FOUND`） */
function notFoundNameFor(table) {
  const stem = table.replace(/^bw_/, '').replace(/^auth_/, '');
  return `${stem.toUpperCase()}_NOT_FOUND`;
}

/** 解析一个 `selectById` 调用点所在的那张表；推不出来就返回 null */
function tableOfCall(owner, receiver) {
  const mapper = typeOfField.get(`${owner}#${receiver}`);
  const entity = mapper ? entityOfMapper.get(mapper) : undefined;
  return {
    mapper: mapper ?? null,
    entity: entity ?? null,
    table: entity ? (tableOfEntity.get(entity) ?? null) : null,
  };
}

/* ------------------------------------------------------ 二、DDL：可空性 */

/** 表名 → { mysql: 'NOT NULL' | 'NULL' | '(无此列)', h2: …, dameng: … } */
const nullability = new Map();
const dialects = [];

for (const { path } of moduleDirs('src', 'main', 'resources', 'db', 'migration')) {
  for (const dialect of readdirSync(path, { withFileTypes: true })) {
    if (!dialect.isDirectory()) continue;
    if (!dialects.includes(dialect.name)) dialects.push(dialect.name);
    const dir = join(path, dialect.name);
    for (const file of readdirSync(dir).filter((name) => name.endsWith('.sql'))) {
      const text = readFileSync(join(dir, file), 'utf8');
      const record = (table, line) => {
        if (!line) return;
        if (!nullability.has(table)) nullability.set(table, {});
        nullability.get(table)[dialect.name] = /NOT\s+NULL/i.test(line) ? 'NOT NULL' : 'NULL';
      };
      // 建表时的列定义
      for (const matched of text.matchAll(
        /CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(([\s\S]*?)\n\s*\)\s*(?:ENGINE|COMMENT|;)/gi,
      )) {
        const line = matched[2]
          .split('\n')
          .find((row) => /^\s*tenant_id\s/i.test(row) && !/^\s*(--|#)/.test(row));
        if (!nullability.has(matched[1])) nullability.set(matched[1], {});
        record(matched[1], line || '');
        if (!line) nullability.get(matched[1])[dialect.name] = '(无此列)';
      }
      // 后来加上的列（V6 / V7 那种 ALTER）
      for (const matched of text.matchAll(
        /ALTER\s+TABLE\s+(\w+)\s+ADD\s+(?:COLUMN\s+)?tenant_id\s+([^\n,;]*)/gi,
      )) {
        record(matched[1], `tenant_id ${matched[2]}`);
      }
    }
  }
}

if (nullability.size === 0) {
  console.log('✗ 一条 DDL 都没解析出来 —— 迁移脚本的目录或写法变了？');
  process.exit(1);
}

/** 这张表的 `tenant_id` 是 `NOT NULL` 吗（以 mysql 为准，三方言一致由规则 B 保证） */
function isTenantScoped(table) {
  const slot = nullability.get(table);
  return Boolean(slot) && slot.mysql === 'NOT NULL';
}

/* ------------------------------------------------ 三、逐调用点收集与判定 */

const inScope = []; // 射程内：表 tenant_id NOT NULL + 抛该表对应的 404xx
const excluded = []; // 命中"selectById + 该表的 404xx"但表不在射程
const foreignCode = []; // selectById 与该表之外对象的 404xx 同方法出现
const badCode = []; // 射程内，assertVisible 的码与抛的码不同
const unresolved = []; // 解析链断掉（防假阴的自检）
const recordExits = []; // 规则 D：抛公共 40400 的"按主键取实体"出口

/* --- 射程**不看枚举**：码是从源码的抛出点里认出来的（改枚举里那句话名不影响它），
       所以这里只做映射链与 DDL 的解析，不去读 `AuthResultCode.java`。 --- */

for (const { shown, text } of sources) {
  const owner = /class\s+(\w+)/.exec(text);
  const ownerName = owner ? owner[1] : null;
  if (!ownerName) continue;

  const members = membersOf(text);

  /**
   * 这个文件里"会抛公共 40400"的方法名 —— 先认**方法体里提到那个码**的，
   * 再把"调用它们的"也算进来（`detail` 抛的是 `notFound()`，码在那一层）。
   * 名字从声明里读，所以规则 D 不需要知道任何一处叫什么。
   */
  const producers = new Set(
    members
      .filter((member) => /ResultCode\s*\.\s*NOT_FOUND/.test(member.body ?? ''))
      .map((member) => nameOf(member.signature)),
  );
  const throwsPublicNotFound = (body) =>
    /ResultCode\s*\.\s*NOT_FOUND/.test(body) ||
    [...producers].some((name) => new RegExp(`\\b${name}\\s*\\(`).test(body));

  for (const member of members) {
    const calls = [...member.body.matchAll(/\b(\w+)\s*\.\s*selectById\s*\(/g)];
    if (calls.length === 0) continue;

    const where = `${shown}:${locate(text, member.bodyStart)}`;
    const who = `${ownerName}#${nameOf(member.signature)}`;

    // 规则 D：既然它是"按主键取实体 + 按不存在抛出"，它就必须回答"这位读者看得见它吗"
    if (throwsPublicNotFound(member.body)) {
      recordExits.push({
        where,
        who,
        landed: recordLandings.some((name) => new RegExp(`\\.\\s*${name}\\s*\\(`).test(member.body)),
      });
    }

    const thrown = [
      ...new Set(
        [...member.body.matchAll(/AuthResultCode\.([A-Z][A-Z0-9_]*)/g)].map((m) => m[1]),
      ),
    ].filter((name) => name.endsWith('_NOT_FOUND'));

    const asserted = [...member.body.matchAll(/\.\s*assertVisible\s*\(/g)].map((matched) => {
      const open = matched.index + matched[0].lastIndexOf('(');
      const code = /AuthResultCode\.([A-Z][A-Z0-9_]*)/.exec(member.body.slice(open, open + 200));
      return { code: code ? code[1] : null };
    });

    for (const call of calls) {
      const resolved = tableOfCall(ownerName, call[1]);
      if (!resolved.table) {
        unresolved.push(
          `${where} ${who} —— \`${call[1]}.selectById\` 推不到表（mapper=${resolved.mapper ?? '未声明为字段'} / entity=${resolved.entity ?? '—'}）`,
        );
        continue;
      }
      if (!nullability.has(resolved.table)) {
        unresolved.push(`${where} ${who} —— 表 \`${resolved.table}\` 在迁移脚本里找不到`);
        continue;
      }

      // 判据 ③：抛的必须是**这张表对应的那一个**码 —— 少了它，取用户却抛归属 404 的
      // 方法（`setPrimaryPosition`）会被误判成"读侧出口"，报一次误报
      const ownCode = notFoundNameFor(resolved.table);
      if (!thrown.includes(ownCode)) {
        const others = thrown.filter((name) => name !== ownCode);
        if (others.length > 0) {
          foreignCode.push(
            `${where} ${who} —— 取 \`${resolved.table}\`、抛 ${others.join('/')}（该表对应的是 ${ownCode}）`,
          );
        }
        continue;
      }

      const entry = {
        where,
        who,
        table: resolved.table,
        code: ownCode,
        asserted: asserted.length > 0,
        codes: asserted.map((item) => item.code),
      };
      if (!isTenantScoped(resolved.table)) {
        excluded.push(entry);
        continue;
      }
      inScope.push(entry);
      if (entry.asserted && !entry.codes.includes(ownCode)) {
        badCode.push(
          `${entry.where} ${entry.who} —— 抛 ${ownCode}，却把 ${entry.codes.join('/') || '（形参）'} 交给 assertVisible`,
        );
      }
    }
  }
}

console.log(`  扫描 ${sources.length} 个源文件 · ${tableOfEntity.size} 个实体表 · ${dialects.length} 条方言 · ${nullability.size} 张表`);
console.log(`    ${dialects.sort().join(' / ')}`);
console.log(`  接上了租户服务的模块（规则 C 的射程）：${[...adopters].sort().join(' · ')}`);
console.log(`  记录管道的落点（规则 D，从声明里读出）：${recordLandings.join(' · ')}`);

/* -------------------------------------------------------------------- 判定 */

check(
  '记录管道的读侧出口都过了那一格落点',
  recordExits.every((item) => item.landed),
  recordExits.length === 0
    ? '射程为空 —— 公共 40400 的抛出点一处都没解析到？'
    : recordExits.filter((item) => !item.landed).length > 0
      ? recordExits
          .filter((item) => !item.landed)
          .map((item) => `${item.where} ${item.who} 抛公共 40400、按主键取实体，却没过落点`)
          .join(' / ') +
        ' —— 少了它，跨租户的 id 与"不属于任何租户"的记录都会得到一个"安静但错误"的答案（0025 第 2 条 · 0077）'
      : `${recordExits.length} 处：${recordExits.map((item) => item.who).join(' · ')}`,
);

check(
  '映射链完整（每个 selectById 都能推到一张存在于 DDL 的表）',
  unresolved.length === 0,
  unresolved.length > 0
    ? unresolved.join(' / ') +
      ' —— 推不出表名就不会被规则 A 覆盖，属**假阴**，所以这里必须红'
    : '表名来自 @TableName、Mapper 泛型与字段声明，都是字面量',
);

check(
  '按主键取实体的路径都过了可见性判定',
  inScope.every((item) => item.asserted),
  inScope.length === 0
    ? '射程为空 —— 映射链或 DDL 解析坏了？'
    : inScope.filter((item) => !item.asserted).length > 0
      ? inScope
          .filter((item) => !item.asserted)
          .map((item) => `${item.where} ${item.who} 取 ${item.table} 却没过判定`)
          .join(' / ') +
        ' —— 跨租户的 id 会得到一个"安静但错误"的答案：它说"这个对象不属于你"，而不是"它不存在"（0025 第 2 条 · 0074）'
      : `${inScope.length} 处：${inScope.map((item) => item.who).join(' · ')}`,
);

check(
  'assertVisible 的码与同方法里"不存在"那一支逐字相同',
  badCode.length === 0,
  badCode.length > 0
    ? badCode.join(' / ') +
      ' —— 同一个 requireXxx 里"不存在"与"看不见"是同一个对象，只可能有一个码（0059 第二节 · 0075）'
    : '射程内每一处都同一个码',
);

const inconsistent = [...nullability.entries()].filter(([, slot]) => {
  const values = dialects.map((dialect) => slot[dialect] ?? '(无此列)');
  return new Set(values).size > 1;
});

check(
  '三方言的 tenant_id 存在性与可空性一致',
  inconsistent.length === 0,
  inconsistent.length > 0
    ? inconsistent
        .map(([table, slot]) => `${table}（${dialects.map((d) => `${d}=${slot[d] ?? '(无此列)'}`).join(' ')}）`)
        .join(' / ') + ' —— 规则 A / D 的射程判据不能因方言而异；dameng 没有被 CI 真跑过，分裂只能靠这里发现'
    : `${nullability.size} 张表在 ${dialects.length} 条方言上写法一致`,
);

const handRolled = new Map(); // `${文件}:${行}` → 列（同一行被两条形状命中时只报一次）
const noteHandRolled = (shown, text, index) => {
  const where = `${shown}:${locate(text, index)}`;
  const key = where.slice(0, where.lastIndexOf(':'));
  if (!handRolled.has(key)) handRolled.set(key, where);
};
for (const { module, shown, text } of sources) {
  if (!adopters.has(module)) continue; // 没跟租户服务打过交道的模块不在此射程，见脚本头对规则 C 的说明
  // 形状一：`x.getTenantId().equals(y)`
  for (const matched of text.matchAll(/getTenantId\s*\(\s*\)\s*\.\s*equals\s*\(/g)) {
    noteHandRolled(shown, text, matched.index);
  }
  // 形状二：`Objects.equals(x.getTenantId(), y)` —— 取**配平**的实参再看里面有没有那个调用
  // （`equals(x, f(y.getTenantId()))` 这种嵌套也能覆盖，而这正是"取到第一个 `)`"会漏掉的）
  for (const matched of text.matchAll(/\bequals\s*\(/g)) {
    const open = matched.index + matched[0].lastIndexOf('(');
    if (/getTenantId\s*\(\s*\)/.test(argumentOf(text, open))) {
      noteHandRolled(shown, text, matched.index);
    }
  }
  // 形状三 / 四：`x.getTenantId() != y` 与 `== y` —— 三条记录管道在 0077 之前的原文写法
  // （`entity.getTenantId().longValue() != tenantId`）。
  // **排除另一侧是 `null` 字面量的那一种**：那是判空，不是"同一个租户吗" ——
  // 把它算进来就是误报，而**会误报的守卫比没有更糟**（0064）。
  for (const matched of text.matchAll(/([!=])={1,2}/g)) {
    // 只看 `!=` 与 `==`；`>=` / `<=` 不在此列（它们左边不会是租户 id）
    if (matched[0] === '!=' || matched[0] === '==') {
      const op = matched.index;
      const left = text.slice(Math.max(0, op - 120), op);
      const right = text.slice(op + matched[0].length, op + matched[0].length + 120);
      if (!/getTenantId\s*\(\s*\)\s*\.\s*longValue\s*\(\s*\)\s*$|getTenantId\s*\(\s*\)\s*$/.test(left)) {
        continue;
      }
      if (/^\s*null\b/.test(right)) continue; // 判空，不是比较两个租户
      noteHandRolled(shown, text, op);
    }
  }
}

check(
  `接上了租户服务的模块里没有手搓的租户比较（${[...adopters].sort().join(' · ') || '无'}）`,
  handRolled.size === 0,
  handRolled.size > 0
    ? [...handRolled.values()].join(' / ') +
      ' —— 租户判定只有落点可写：assertVisible（已存在对象）/ assertReferencedInSameTenant（写侧引用）/ resolveWriteTenant（建对象）/ ' +
      recordLandings.join(' / ') +
      '（记录管道，0077）；手搓一份就是又一个口径（0075 未覆盖 2）'
    : '租户 id 只被交给落点，或写进实体与 SQL 条件',
);

/* --------------------------------------------------------------- 供人核对 */

console.log('');
console.log(`  射程 ${inScope.length} 处 · 由表推导排除 ${excluded.length} 处 · 记录管道出口 ${recordExits.length} 处`);
for (const item of excluded) {
  const slot = nullability.get(item.table);
  const why =
    slot && slot.mysql === '(无此列)'
      ? '没有 tenant_id 列（它本身是租户，可见性由 requireVisibleTenant 单独说）'
      : 'tenant_id 可空（平台级对象恒为 NULL ⇒ assertVisible 是空操作）';
  console.log(`    ${item.table} : ${why} ⇒ ${item.who} 不判`);
}
if (foreignCode.length > 0) {
  console.log(`  另有 ${foreignCode.length} 处 selectById 与"别的对象"的 404xx 同方法出现（**不判**，语义写在那一处的注释里）：`);
  for (const line of foreignCode) console.log(`    ${line}`);
}

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  "读侧按 id 取实体的路径都过判定"此前靠 0074 的一次手工核对，现在有机器兜底。');
console.log('  "记录管道的出口只有一格、且没有平台级出口"此前只是一条没人点破的用例断言（0077）。');
console.log('  "查询路径的租户条件从哪来"仍要人工 —— 见 docs/decisions/0077 的「未覆盖」。');
