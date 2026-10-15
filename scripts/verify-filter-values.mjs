#!/usr/bin/env node
/**
 * 过滤值的规范化 —— `server/` 里每一处**精确匹配**的过滤值都必须过 `FilterValue.text`。
 *
 * ## 为什么需要它
 *
 * `status` / `app` / `traceId` 这类过滤值是**精确匹配**（`eq`）的，而各域写库前都过了一遍
 * 规范化 ⇒ 库里的值不会带两端空白 ⇒ `?status=%20ENABLED%20` **永远**匹配不到任何行。
 * 这条口径此前散在四处（组织 / 角色 / 用户组会 trim，用户 / 租户 / 日志 / 事件 / 审计把原值
 * 直接交给 `eq`），于是同一个 `status` 在组织域搜得到、在用户域搜不到 ——
 * `FilterValueTrimApiTest` 有两个域的同形断言摆着（`docs/decisions/0069`）。
 *
 * 更要紧的是它还顺手消除了一处内部不一致：日志 / 事件 / 审计的聚合走**手写 SQL**，
 * 那里的判空是 Ognl 的 `<if test="app != null and app != ''">`，而同一请求的列表走
 * Java 的 `hasText()`（`isBlank`）—— 两者对 `"   "` 的判定**相反**，
 * 症状是同一个请求里"总数 3、各级别之和 0"。
 *
 * 于是这里剩下两个会腐烂的地方：**新加一处过滤值时又直接 `eq(query.getX())`**、
 * **把原始值直接喂给手写 SQL**。两种写法读起来都完全正常，测试也照样绿
 * （新过滤值本来就没有"带空白搜得到"那一支断言），只有"从别处粘贴过来带了个空格"
 * 这个没人会去试的角落会露出来。
 *
 * 它跟 `0063` / `0067` 那类守卫同性质：锚定的是**无歧义的 token 形状**
 * （`hasText(` 这个方法名、`isBlank()` 这个 String 专属方法、`countBy*` 这个方法名），
 * 扫到就是扫到 —— 不是 `0064` 那种要理解语义、会误报的判定。
 *
 * ## 它查什么
 *
 * 1. `.eq(...)` 的条件里**没有** `hasText(...)` —— 那正是"值没规范化、拿判空当开关"的旧形态。
 * 2. `.eq(...)` 的条件里**没有** `query.getX().isBlank()` / `.isEmpty()` —— 同上，
 *    只不过写成了内联判断（用户域与租户域原本就是它）。
 * 3. 三个**手写聚合 SQL**（`countByLevel` / `countByEventName` / `countByAction`）的实参里
 *    **没有** `query.getXxx()` —— 直传原始值会让它们与同请求的 lambda wrapper
 *    对同一个参数给出不同判定（两条路径必须同源：同一个 `FilterValue.text` 的产物）。
 * 4. **枚举列**的过滤值（`query.getStatus` / `getResourceType` / `getMemberType` / `getLevel`
 *    / `getResult`）只能作为 `FilterValue.enumValue(...)` 的第一个实参出现 —— 出现别处
 *    就是"绕开了归一"（`docs/decisions/0070`）。这一条的锚点比前三条更强：它不是"某个名字
 *    出现得可疑"，而是**这五个 getter 在这五个 Query 类里只有这一个正当用法**，
 *    所以可以要求"每一次出现都必须在 `enumValue(` 里"，而不是只揪某种坏写法。
 * 5. **上报侧**（日志 / 事件两个域）写进实体的那些过滤列，实参里必须有 `FilterValue`
 *    （`docs/decisions/0071`）。这一条守的是同一枚硬币的另一面：前四条管"读的时候归一"，
 *    这一条管"写的时候也归一"—— 只要有一侧漏了，读侧的宽容就是**空转**：
 *    上报 `app = " myapp "` 会原样落库，而 `?app=myapp` 被规范成 `myapp`，一条也匹配不到。
 *    （射程只到这两个模块，理由见下面「刻意不查的」。）
 *
 * ## 用法
 *
 *     node scripts/verify-filter-values.mjs
 *
 * 不需要任何前置条件（只读源码文本，不编译、不连网、不写文件），失败时非零退出。
 *
 * ## 刻意不查的（写在这里免得后来者以为它漏了）
 *
 * - **"新加的过滤值有没有真的过 `FilterValue.text`"**：没有一个无歧义的 token 能锚定它 ——
 *   `.eq(x != null, Col::getX, x)` 里的 `x` 是不是 `FilterValue.text(...)` 的产物，
 *   要判它就得做数据流分析。而**合法的 `!= null` 到处都是**（`tenantId` / `parentId` /
 *   `operatorId` … 都是 Long，本来就不该规范化），锚定 `!= null` 会大面积误报
 *   ⇒ 按 `0064` 的判据（会误报的守卫比没有更糟）不做。
 * - **`entity.setStatus(...)` 的实参**：同一个 setter 在四个域有四种写法
 *   （`resolveStatus(...)` · 调用方算好的局部变量 `status` · `Status.ENABLED.wireName()` ·
 *   直接字面量），**文本上分不出"已归一"与"没归一"** —— 角色域的 `applyTo` 写的就是
 *   `entity.setStatus(status)`，那个 `status` 是调用方归一后传进来的。锚定它会误报，
 *   按 `0064` 不做；那两处 `update` 由 `WriteSideNormalizationApiTest` 钉着。
 * - **`vo.setXxx(...)` / `context.setUserId(...)` / 关联表写入**：同名 setter 全仓到处都是
 *   （`setUserId` 就有八处与过滤无关）⇒ 规则 5 把射程限定在 **`log` / `track` 两个上报模块**、
 *   且只认接收者是 `entity` 的那一类。代价是白名单固化（与规则 4 同一个代价）。
 * - **`entity.setLevel(...)`**：日志域的 `level` 也是过滤列，但它的实参是
 *   `level.wireName()`（枚举归一，见 `0070`），不含 `FilterValue` ⇒ 纳入锚点会误报。
 * - **`email` / `phone` 的写入**（`UserService.blankToNull`，不 trim）：它们参与的是**判重**
 *   而不是过滤，读侧是 `existsXxx` 而非 `eq ... where`。同源问题，但需要单独量一遍判重入口 ——
 *   见 `docs/decisions/0071` 的「未覆盖」。
 * - **`.eq(...)` 里的判空一律揪住**：只揪 `isBlank()` / `isEmpty()` 这类**String 专属**的 token，
 *   正是因为它们不可能出现在 Long 上、零误报。
 * - **"规范化之后的模式串在三方言上是否同义"**：那是语义，靠 `FilterValueTrimApiTest`
 *   （跨 profile）钉，不靠文本扫描。
 * - **审计域的 `baseWrapper(tenantId, resource, operatorId)` 收到未规范化的 `resource`**：
 *   它的实参里混着 `operatorId`（Long），锚定 `query.get` 会误报（见规则 3 的注释）。
 *   那两处调用点与算好 `resource` 的语句相邻，靠评审查得住。
 * - **审计域的写入不在射程**（`AuditInterceptor` / `AuditRecorder`）：那些值取自
 *   `@Audited` 注解与上下文，**不由外部输入决定**，因此没有"客户端传了个带空格的"这条路。
 * - **`isBlank`（Unicode 空白）与 `trim`（只吃 `<= U+0020`）的覆盖面之差**：
 *   见 `docs/decisions/0069` 的「未覆盖」—— 那是一个待裁决的口径问题，不是守卫能判的。
 * - **"新加的枚举列有没有归一"**：规则 4 的五个 getter 名是**枚举列的全集**（当前如此），
 *   但"下一个域又加一个枚举列、用的是新 getter 名"这件事没有 token 能锚定 ——
 *   只能靠"枚举列的过滤值走 `enumValue`"这条纸面规则，见 `docs/decisions/0070`。
 *   反过来说：**规则 4 零误报**的前提正是"这五个 getter 只用于过滤"，改了这五个 Query
 *   的任何一处非过滤用途，就会看到它报红 —— 那是提醒你去改这个白名单，不是让你忽略它。
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SERVER = join(ROOT, 'server');

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

// 规则 1：`.eq(` 的条件里出现 `hasText(`
const HAS_TEXT_IN_EQ_RE = /\.eq\([^\n]*?\bhasText\s*\(/;
// 规则 2：`.eq(` 的条件里出现 `query.getXxx().isBlank()` / `.isEmpty()`（String 专属，零误报）
const STRING_BLANK_IN_EQ_RE = /\.eq\([^\n]*?query\.get[A-Za-z]+\(\)\s*\.\s*(?:isBlank|isEmpty)\s*\(\)/;
// 规则 3：三个**手写聚合 SQL** 的实参里出现 `query.getXxx()`。
//
// **不加 `.` 前缀**：它们是同类内/跨类调用，写法是 `xxxMapper.countByLevel(...)` 与
// 形如 `selectCount(baseWrapper(...))` 的嵌套。
//
// **刻意不含 `baseWrapper`**：它的三个实参里有一个是 `operatorId`（**Long**），
// 而调用点写的是 `visibility.operatorFilter(query.getOperatorId())` —— 那是正当的
// （ID 不需要规范化），锚定 `query.get` 会误报。按 `0064` 的判据：会误报的守卫比没有更糟。
// 而 `countByLevel` / `countByEventName` / `countByAction` 的实参**全是**过滤值
// （app / resource），锚定它是零误报的。
const AGG_CALL_RE = /\b(countByLevel|countByEventName|countByAction)\s*\(/g;
// 规则 4：**枚举列**的过滤值 getter。这五个名字在全部 `*Query.java` 里一一对应
// `status`（用户 / 租户 / 组织 / 角色）· `resourceType` · `memberType`（角色成员 / 用户组成员）
// · `level`（日志）· `result`（审计），且**没有第二个含义** —— 所以它们是零误报的锚点。
const ENUM_FILTER_GETTER_RE = /query\.get(?:Status|Level|Result|ResourceType|MemberType)\(\)/g;
// 规则 5：**上报侧**写入实体时用的过滤列 setter。只有日志 / 事件两个域是"客户端把值
// 直接送进来"，所以射程限定在这两个模块里、且接收者写作 `entity` 的那一类
// （`vo.setXxx(...)` 装的是从库里读出来的值，`context.setUserId(...)` 是别的东西）。
//
// **不含 `Level`**：日志域的 level 也是过滤列，但它的实参已经是 `level.wireName()`
// （枚举归一走 `ClientLogLevel.parse`，见 0070），拿 `FilterValue` 要求它只会误报。
const REPORT_COLUMN_SETTER_RE = /entity\.set(App|Namespace|TraceId|SessionId|UserId|DeviceId)\s*\(/g;
const REPORT_MODULES = ['sts-server-log', 'sts-server-track'];

/** 从 `(` 起取到配对的 `)`，返回实参文本 —— 这些调用会跨行（`truncate(\n FilterValue.text(…), 64)`）。 */
function argsOf(text, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < text.length; i += 1) {
    if (text[i] === '(') depth += 1;
    else if (text[i] === ')') {
      depth -= 1;
      if (depth === 0) return text.slice(openIndex + 1, i);
    }
  }
  return text.slice(openIndex + 1);
}

const sources = collectMainSources();
const hasTextInEq = [];
const blankInEq = [];
const rawIntoAggregate = [];
const enumGetterOutsideEnumValue = [];
const rawIntoReportedColumn = [];

for (const file of sources) {
  const raw = readFileSync(file, 'utf8');
  const text = stripComments(raw);
  const lines = text.split('\n');

  lines.forEach((line, index) => {
    if (HAS_TEXT_IN_EQ_RE.test(line)) {
      hasTextInEq.push({ file, line: index + 1, text: line.trim() });
    }
    if (STRING_BLANK_IN_EQ_RE.test(line)) {
      blankInEq.push({ file, line: index + 1, text: line.trim() });
    }
  });

  // 规则 3 要跨行：从调用点起截到该语句的 `;`（这些聚合调用都很简单，不会有带分号的 lambda）
  for (const matched of text.matchAll(AGG_CALL_RE)) {
    const tail = text.slice(matched.index, matched.index + 600);
    const end = tail.indexOf(';');
    const statement = end === -1 ? tail : tail.slice(0, end);
    if (/query\.get[A-Za-z]+\(\)/.test(statement)) {
      rawIntoAggregate.push({
        file,
        line: text.slice(0, matched.index).split('\n').length,
        method: matched[1],
      });
    }
  }

  // 规则 4：枚举列的过滤值只允许作为 `enumValue(` 的第一个实参出现。
  // 判据是"**本条语句**里、它之前最近的那个函数名是不是 enumValue(" ——
  // 用 `;` 划语句边界，于是多行调用 `enumValue(\n  X::parse, …)` 也认得出来。
  for (const matched of text.matchAll(ENUM_FILTER_GETTER_RE)) {
    const before = text.slice(0, matched.index);
    if (before.lastIndexOf('enumValue(') > before.lastIndexOf(';')) {
      continue;
    }
    enumGetterOutsideEnumValue.push({
      file,
      line: before.split('\n').length,
      getter: matched[0],
    });
  }

  // 规则 5：上报侧写进实体的过滤列，实参里必须有 FilterValue。
  if (REPORT_MODULES.some((name) => file.includes(name))) {
    for (const matched of text.matchAll(REPORT_COLUMN_SETTER_RE)) {
      const openIndex = matched.index + matched[0].length - 1;
      if (argsOf(text, openIndex).includes('FilterValue')) {
        continue;
      }
      rawIntoReportedColumn.push({
        file,
        line: text.slice(0, matched.index).split('\n').length,
        setter: matched[1],
      });
    }
  }
}

const rel = (file) => file.slice(ROOT.length + 1).replace(/\\/g, '/');

/* -------------------------------------------------------------------- 判定 */

check(
  '扫到了 `server/` 的源码',
  sources.length > 0,
  sources.length === 0 ? '一个 `src/main/java` 源文件都没扫到 —— 目录结构变了？' : `扫描 ${sources.length} 个源文件`,
);

check(
  '过滤值的判空没有用 `hasText(...)` 当开关',
  hasTextInEq.length === 0,
  hasTextInEq.length === 0
    ? '`hasText` 只服务于"条目校验 / 取第一个非空值"，不参与过滤条件的拼装'
    : hasTextInEq
        .map(
          (item) =>
            `${rel(item.file)}:${item.line} —— ${item.text.slice(0, 90)}` +
            ' —— 过滤值请先过 FilterValue.text(...)、再拿它的结果判 null（decisions/0069）',
        )
        .join('\n    '),
);

check(
  '过滤值的判空没有内联写成 `query.getX().isBlank()`',
  blankInEq.length === 0,
  blankInEq.length === 0
    ? '没有"不规范化、只判空白"的内联写法'
    : blankInEq
        .map(
          (item) =>
            `${rel(item.file)}:${item.line} —— ${item.text.slice(0, 90)}` +
            ' —— 同一个值在别的域是规范化的，这里判空白会让它「在某些页面上搜不到」（decisions/0069）',
        )
        .join('\n    '),
);

check(
  '手写聚合 SQL 收到的是规范化后的结果，不是 `query.getXxx()`',
  rawIntoAggregate.length === 0,
  rawIntoAggregate.length === 0
    ? '三处聚合（日志级别 / 事件名 / 审计动作）都与同请求的列表用同一个局部变量'
    : rawIntoAggregate
        .map(
          (item) =>
            `${rel(item.file)}:${item.line} 的 ${item.method}(...)` +
            ' —— 手写 SQL 的判空是 Ognl 的 `!= \'\'`、列表走 Java 的 isBlank，两者对空白判定相反；' +
            '改成传 FilterValue.text(query.getX()) 的结果（decisions/0069）',
        )
        .join('\n    '),
);

check(
  '枚举列的过滤值都过了 `FilterValue.enumValue(...)`',
  enumGetterOutsideEnumValue.length === 0,
  enumGetterOutsideEnumValue.length === 0
    ? 'status / resourceType / memberType / level / result 五处只在 enumValue 里露面 —— '
      + '换个大小写不是另一个对象，归一它是修正写法（decisions/0070）'
    : enumGetterOutsideEnumValue
        .map(
          (item) =>
            `${rel(item.file)}:${item.line} —— ${item.getter}` +
            ' —— 枚举列的过滤值必须走 FilterValue.enumValue(...)；绕开它会让'
            + '「同一个 status 在一个域搜得到、在另一个域搜不到」（decisions/0070）',
        )
        .join('\n    '),
);

check(
  '上报侧写进实体的过滤列都过了 `FilterValue`',
  rawIntoReportedColumn.length === 0,
  rawIntoReportedColumn.length === 0
    ? '日志 / 事件两个域的 app / namespace / traceId / sessionId / userId / deviceId 写入时与过滤侧同源 —— '
      + '写进去什么样子，就得能搜到什么样子（decisions/0071）'
    : rawIntoReportedColumn
        .map(
          (item) =>
            `${rel(item.file)}:${item.line} 的 entity.set${item.setter}(...)` +
            ' —— 这几列会被 `?app=` / `?namespace=` / … 精确匹配，而过滤侧是把值 trim 干净再比的：'
            + '原样入库会让"上报时多打了两个空格"变成一条**永远搜不到**的记录；'
            + '改成 FilterValue.text(...)（先 trim 再截断，顺序不能反）（decisions/0071）',
        )
        .join('\n    '),
);

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  它守的是"精确匹配的过滤值与写进库里的值只有一种写法"；');
console.log('  为什么这么定、以及哪些没覆盖，见 docs/decisions/0069 · 0070 · 0071。');
