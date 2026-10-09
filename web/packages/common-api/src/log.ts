import type { HttpClient } from 'common-tools/http';

import type { ApiSchemas, BodyOf, DataOf, JsonArray, JsonObject, PathOf, QueryOf } from './types';
import type { paths } from './generated/schema';

/** 契约中日志域各操作的原始定义 */
type ReportOp = paths['/api/logs/client']['post'];
type PageOp = paths['/api/logs/client']['get'];
type DetailOp = paths['/api/logs/client/{id}']['get'];
type TraceOp = paths['/api/logs/client/trace/{traceId}']['get'];
type StatsOp = paths['/api/logs/client/stats']['get'];

/**
 * 修正契约中的"任意值"字段。
 *
 * <p>`data` / `context` 在后端是 `Object`，生成物把它们渲染成了
 * `Record<string, never>`（含义：一个不能有任何字段或元素的对象）。
 * 见 {@link JsonValue} 的说明 —— 在这里统一修正这两个字段，
 * 其余字段仍原样取自契约。
 */
type WithJsonPayload<T> = Omit<T, 'data' | 'context'> & {
  data?: JsonArray;
  context?: JsonObject;
};

/** 单条上报日志，字段与前端 `LogRecord` 一一对应 */
export type ClientLogItem = WithJsonPayload<ApiSchemas['ClientLogItem']>;

/**
 * 修正上报请求里的 `logs` 数组元素。
 *
 * <p>契约里的 `logs` 引用的是**生成物中的** `ClientLogItem`，
 * 那份仍是 `data: Record<string, never>[]` 的错误形态。这里只替换元素类型，
 * 请求体的其余结构（是否还有别的字段、哪个字段必填）依然完全由契约决定 ——
 * 不会因为手写而悄悄与契约脱节。
 */
type WithJsonLogs<T> = Omit<T, 'logs'> & { logs: ClientLogItem[] };

/** 批量上报请求（与 {@link ClientLogReportBody} 等价，后者从接口操作推导而来） */
export type ClientLogReportRequest = WithJsonLogs<ApiSchemas['ClientLogReportRequest']>;

/** 批量上报结果 */
export type ClientLogReportResult = ApiSchemas['ClientLogReportResultVO'];

/** 服务端返回的日志记录 */
export type ClientLogVO = WithJsonPayload<ApiSchemas['ClientLogVO']>;

/** 单级别计数 */
export type ClientLogLevelCount = ApiSchemas['ClientLogLevelCount'];

/** 查询条件 —— 直接从契约的 query 参数推导，含继承自 PageQuery 的 page / size */
export type ClientLogQuery = QueryOf<PageOp>;

/** 聚合查询条件 */
export type ClientLogStatsQuery = QueryOf<StatsOp>;

/** 分页结果 */
export type ClientLogPage = NonNullable<DataOf<PageOp>>;

/** 聚合结果 */
export type ClientLogStats = NonNullable<DataOf<StatsOp>>;

/**
 * 日志域接口。
 *
 * <p><b>这里没有任何一个字段名或类型是手写的</b>：全部经由 `types.ts` 的工具从契约推导。
 * 手写的只有"哪个方法对应哪条路径"这层接线 —— 而路径字面量受 `keyof paths` 约束，
 * 拼错会在编译期直接报错。（见下方 `satisfies` 的用法。）
 */
export interface LogApi {
  /** 批量上报，单批最多 500 条 */
  report(body: ClientLogReportRequest): Promise<ClientLogReportResult>;
  /** 分页查询 */
  page(query?: ClientLogQuery): Promise<ClientLogPage>;
  /** 查询详情 */
  detail(id: PathOf<DetailOp>['id']): Promise<ClientLogVO>;
  /** 按链路标识取整条链路（按时间升序） */
  listByTraceId(traceId: PathOf<TraceOp>['traceId']): Promise<ClientLogVO[]>;
  /** 按级别聚合统计 */
  stats(query?: ClientLogStatsQuery): Promise<ClientLogStats>;
}

/** 上报接口的请求体类型（供上报缓冲复用，避免各处再拼一遍字段） */
export type ClientLogReportBody = WithJsonLogs<NonNullable<BodyOf<ReportOp>>>;

/** 路径常量。`satisfies keyof paths` 让拼错的路径在编译期就暴露 */
const PATH_CLIENT_LOG = '/api/logs/client' satisfies keyof paths;
const PATH_CLIENT_LOG_STATS = '/api/logs/client/stats' satisfies keyof paths;

export function createLogApi(http: HttpClient): LogApi {
  return {
    report: (body) => http.post<ClientLogReportResult>(PATH_CLIENT_LOG, body),
    page: (query) => http.get<ClientLogPage>(PATH_CLIENT_LOG, { query }),
    detail: (id) => http.get<ClientLogVO>(`${PATH_CLIENT_LOG}/${id}`),
    // traceId 必须编码：它常由客户端生成，可能带 `:` / `/` 等字符，
    // 不编码时会被当成路径分隔符，请求打到另一个接口上（表现为 404 或 405）
    listByTraceId: (traceId) =>
      http.get<ClientLogVO[]>(`${PATH_CLIENT_LOG}/trace/${encodeURIComponent(traceId)}`),
    stats: (query) => http.get<ClientLogStats>(PATH_CLIENT_LOG_STATS, { query }),
  };
}
