import type { HttpClient } from 'common-tools/http';

import type { ApiSchemas, BodyOf, DataOf, PathOf, QueryOf, WithJsonProps } from './types';
import type { paths } from './generated/schema';

/** 契约中事件域各操作的原始定义 */
type ReportOp = paths['/api/events/client']['post'];
type PageOp = paths['/api/events/client']['get'];
type DetailOp = paths['/api/events/client/{id}']['get'];
type StatsOp = paths['/api/events/client/stats']['get'];

/**
 * 单条上报事件，字段与前端 `common-tools` 的 `EventEnvelope` 一一对应。
 *
 * <p>只替换 `props` 的形态（生成物给的是未收紧的对象），其余字段完全由契约决定。
 */
export type ClientEventItem = WithJsonProps<ApiSchemas['ClientEventItem']>;

/** 修正上报请求里的 `events` 数组元素 */
type WithJsonEventItems<T> = Omit<T, 'events'> & { events: ClientEventItem[] };

/** 修正分页结果里的 `records` 数组元素 —— 理由同日志域：生成物里的元素未经收紧 */
type WithJsonEventRecords<T> = Omit<T, 'records'> & { records: ClientEventVO[] };

/** 批量上报请求（与 {@link ClientEventReportBody} 等价，后者从接口操作推导而来） */
export type ClientEventReportRequest = WithJsonEventItems<ApiSchemas['ClientEventReportRequest']>;

/** 上报接口的请求体类型（供上报缓冲复用，避免各处再拼一遍字段） */
export type ClientEventReportBody = WithJsonEventItems<NonNullable<BodyOf<ReportOp>>>;

/** 批量上报结果 */
export type ClientEventReportResult = ApiSchemas['ClientEventReportResultVO'];

/** 服务端返回的事件记录 */
export type ClientEventVO = WithJsonProps<ApiSchemas['ClientEventVO']>;

/** 单事件名计数 */
export type ClientEventNameCount = ApiSchemas['ClientEventNameCount'];

/** 查询条件 —— 直接从契约的 query 参数推导，含继承自 PageQuery 的 page / size */
export type ClientEventQuery = QueryOf<PageOp>;

/** 聚合查询条件 */
export type ClientEventStatsQuery = QueryOf<StatsOp>;

/** 分页结果 */
export type ClientEventPage = WithJsonEventRecords<NonNullable<DataOf<PageOp>>>;

/** 聚合结果 */
export type ClientEventStats = NonNullable<DataOf<StatsOp>>;

/**
 * 行为事件域接口。
 *
 * <p>与日志域同样的原则：**没有任何一个字段名或类型是手写的**，
 * 全部经 `types.ts` 的工具从契约推导。手写的只有"哪个方法对应哪条路径"这层接线，
 * 而路径字面量受 `keyof paths` 约束，拼错会在编译期直接报错。
 */
export interface EventApi {
  /** 批量上报，单批最多 500 条 */
  report(body: ClientEventReportRequest): Promise<ClientEventReportResult>;
  /** 分页查询 */
  page(query?: ClientEventQuery): Promise<ClientEventPage>;
  /** 查询详情 */
  detail(id: PathOf<DetailOp>['id']): Promise<ClientEventVO>;
  /** 按事件名聚合统计 */
  stats(query?: ClientEventStatsQuery): Promise<ClientEventStats>;
}

/** 路径常量。`satisfies keyof paths` 让拼错的路径在编译期就暴露 */
const PATH_CLIENT_EVENT = '/api/events/client' satisfies keyof paths;
const PATH_CLIENT_EVENT_STATS = '/api/events/client/stats' satisfies keyof paths;

export function createEventApi(http: HttpClient): EventApi {
  return {
    report: (body) => http.post<ClientEventReportResult>(PATH_CLIENT_EVENT, body),
    page: (query) => http.get<ClientEventPage>(PATH_CLIENT_EVENT, { query }),
    detail: (id) => http.get<ClientEventVO>(`${PATH_CLIENT_EVENT}/${id}`),
    stats: (query) => http.get<ClientEventStats>(PATH_CLIENT_EVENT_STATS, { query }),
  };
}
