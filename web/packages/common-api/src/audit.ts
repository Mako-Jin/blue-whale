import type { HttpClient } from 'common-tools/http';

import type { ApiSchemas, DataOf, PathOf, QueryOf } from './types';
import type { paths } from './generated/schema';

/**
 * 审计域的接线。
 *
 * <h3>它和另外两个域最不一样的地方：没有写入方法</h3>
 *
 * `log.ts` 有 `report()`、`events.ts` 有 `report()`、`access.ts` 有增删改 ——
 * 本文件**一个写方法都没有**，而这正是审计管道的核心设计（`docs/decisions/0019`）：
 * 审计记录由**后端拦截器自动产生**，前端只能读。
 *
 * 不可篡改在这里不是靠权限码保护的（有权限的人照样能改），而是靠
 * **接口根本不存在** —— 没有 POST / PUT / DELETE `/api/audit/logs`。
 * 真去调这些方法，后端返回 40500（方法不支持），
 * 因此这里连方法都不提供，就不会有人以为"调一下就能补一条记录"。
 *
 * 换句话说：**本文件缺少的东西，比它拥有的东西更能说明架构。**
 *
 * <h3>审计**没有客户端时间戳**</h3>
 *
 * 日志与行为事件都带双时间戳（`clientTime` 仅展示、`serverTime` 是排序过滤的唯一依据），
 * 因为它们是客户端产生的、客户端的时间不可信。审计不由客户端产生，
 * 所以只有 `serverTime` 一个时间 —— 没有"两个时间该信哪个"的问题。
 */

/* ------------------------------------------------------------------ 操作定义 */

type PageOp = paths['/api/audit/logs']['get'];
type DetailOp = paths['/api/audit/logs/{id}']['get'];
type StatsOp = paths['/api/audit/logs/stats']['get'];

/* -------------------------------------------------------------- 结果与动作标识 */

/**
 * 审计结果。
 *
 * <p>契约里是 `string`（springdoc 把 Java 枚举渲染成了字符串），这里收紧成字面量联合。
 * 收紧的判据是「后端是一个封闭枚举」（`AuditResult`），**不是**"我想改一改"——
 * 前者让 `record.result === 'SUCCESS'` 里的拼写错误在编译期就暴露，
 * 后者只会让契约与前端各自演化。
 */
export const AuditResults = {
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE',
} as const;

/** 审计结果的字面量联合类型 */
export type AuditResult = (typeof AuditResults)[keyof typeof AuditResults];

/**
 * 审计**动作标识**（后端 `AuthAuditActions` 的子集）。
 *
 * <p>与权限码一样，动作标识是**永久契约**：它落进历史数据，
 * 改名等于把历史记录切成两半（`docs/decisions/0019`）。所以前端也不许现拼。
 *
 * <p>这里只列**前端真的用到**的两条：演示台的"演练区"要真实调一次写接口，
 * 再确认"刚才那一下有没有被记下来"，判据就是这两条动作标识。
 * 把另外五条也抄过来没有意义 —— 界面不判断它们，抄过来只是一份看起来在管事的假象。
 */
export const AuditActions = {
  TENANT_CREATE: 'auth.tenant.create',
  TENANT_DELETE: 'auth.tenant.delete',
} as const;

/**
 * 审计查询权限。
 *
 * <p>与 `AccessPermissions` 同源（后端 `AuthPermissions`），同样**禁止拼接自造**。
 * 单独放在这里而不是塞进 `AccessPermissions`，是因为它们的消费方不同：
 * `AccessPermissions` 用于"管理界面该不该显示某个按钮"（`platform-admin`），
 * 本常量用于"能不能看审计流水"（`platform-audit-console`）。
 * 合成一张表会让两个应用都拿到一堆自己用不到的标识。
 */
export const AuditPermissions = {
  READ: 'auth:audit:read',
} as const;

/** 审计查询权限的字面量联合类型 */
export type AuditPermission = (typeof AuditPermissions)[keyof typeof AuditPermissions];

/* ------------------------------------------------------------------ 出参 / 入参 */

/**
 * 一条审计记录。
 *
 * <p>字段全部来自契约，唯一的收紧是把 `result` 换成 {@link AuditResult}。
 * 它**不含** `data` / `props` 这类任意 JSON 载荷 —— 审计记的是"谁在何时对什么做了什么、
 * 结果如何"，是**结构化的事实**，不是自由文本。所以本文件也不需要
 * `log.ts` 那套 `WithJsonPayload` 处理。
 */
export type AuditLog = Omit<ApiSchemas['AuditLogVO'], 'result'> & { result?: AuditResult };

/**
 * 修正分页结果里的 `records` 元素类型。
 *
 * <p>理由与 `log.ts` 的 `WithJsonRecords` 相同：契约生成的 `records` 元素是
 * `AuditLogVO`，与对外导出的 {@link AuditLog} **不是同一个类型**（前者的 `result` 还是 `string`）。
 * 不替换的话，`page.records[0]` 就递不进任何期望 `AuditLog` 的地方。
 */
type WithAuditRecords<T> = Omit<T, 'records'> & { records: AuditLog[] };

/** 分页查询条件（含继承自 `PageQuery` 的 `page` / `size`） */
export type AuditLogQuery = Omit<QueryOf<PageOp>, 'result'> & { result?: AuditResult };

/** 分页结果 */
export type AuditLogPage = WithAuditRecords<NonNullable<DataOf<PageOp>>>;

/** 审计记录主键 */
export type AuditLogId = PathOf<DetailOp>['id'];

/** 单个动作的计数 */
export type AuditActionCount = ApiSchemas['AuditActionCount'];

/** 聚合查询条件 */
export type AuditStatsQuery = QueryOf<StatsOp>;

/** 聚合结果 */
export type AuditStats = NonNullable<DataOf<StatsOp>>;

/* ------------------------------------------------------------------ 接口 */

/**
 * 审计域接口。
 *
 * <p>三个方法**全是读** —— 见文件头的说明。若将来有人想加一个 `create`，
 * 那说明对"审计不可篡改"的理解出了偏差：真正的做法是让**写操作本身**
 * 在后端被 `@Audited` 标出来，而不是让前端补一条记录。
 */
export interface AuditApi {
  /** 分页查询审计流水（服务端按 `serverTime` 倒序；时间窗缺省最近 7 天） */
  page(query?: AuditLogQuery): Promise<AuditLogPage>;
  /** 查询单条详情 */
  detail(id: AuditLogId): Promise<AuditLog>;
  /**
   * 按动作聚合统计。
   *
   * <p><b>刻意不接受按 `action` 过滤</b> —— 它本身就是按动作分组的，
   * 允许按动作过滤等于让它永远只返回一条。这与事件域的聚合接口同一个道理：
   * 过滤维度和分组维度重合时，那个过滤条件是没有意义的。
   * 想筛某个动作，用 `page({ action })`。
   */
  stats(query?: AuditStatsQuery): Promise<AuditStats>;
}

/** 路径常量。`satisfies keyof paths` 让拼错的路径在编译期就暴露，而不是运行时 404 */
const PATH_AUDIT_LOGS = '/api/audit/logs' satisfies keyof paths;
const PATH_AUDIT_LOGS_STATS = '/api/audit/logs/stats' satisfies keyof paths;

export function createAuditApi(http: HttpClient): AuditApi {
  return {
    page: (query) => http.get<AuditLogPage>(PATH_AUDIT_LOGS, { query }),
    detail: (id) => http.get<AuditLog>(`${PATH_AUDIT_LOGS}/${id}`),
    stats: (query) => http.get<AuditStats>(PATH_AUDIT_LOGS_STATS, { query }),
  };
}
