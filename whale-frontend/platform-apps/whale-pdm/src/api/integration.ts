// PDM 集成运维 API：同步日志、失败重试、BOM/MES/OA 对账与补偿（经网关 /pdm/** 转发）
//
// 对账只读本地发布记录与同步日志；差异通过重新通知、重推下发或 OA 状态补偿修复。

import { http, type PageResult } from './client';

/** 集成通道 */
export type SyncChannel = 'CAD' | 'ERP' | 'MES' | 'OA';

/** 同步状态 */
export type SyncStatus = 'SUCCESS' | 'FAILED';

/** 集成同步日志 */
export interface SyncLog {
  id: number;
  channel: SyncChannel;
  action: string;
  direction: string;
  bizType?: string;
  bizId?: number;
  bizNo?: string;
  versionNo?: string;
  idempotencyKey: string;
  status: SyncStatus;
  retryCount: number;
  lastError?: string;
  externalRef?: string;
  checksum?: string;
  operatorId?: number;
  requestedAt?: string;
  finishedAt?: string;
  createTime?: string;
}

/** 同步日志查询条件 */
export interface SyncLogQuery {
  current?: number;
  size?: number;
  channel?: SyncChannel;
  action?: string;
  status?: SyncStatus;
  bizType?: string;
  bizNo?: string;
}

/** 对账结论 */
export type MatchStatus = 'MATCH' | 'DIFF' | 'MISSING';

/** 对账结果 */
export interface ReconcileRow {
  id: number;
  channel: SyncChannel;
  bizType: string;
  bizNo: string;
  bizId?: number;
  versionNo?: string;
  localStatus?: string;
  remoteStatus?: string;
  localChecksum?: string;
  remoteChecksum?: string;
  matchStatus: MatchStatus;
  diffSummary?: string;
  checkedAt?: string;
}

/** 对账查询条件 */
export interface ReconcileQuery {
  current?: number;
  size?: number;
  channel?: SyncChannel;
  bizType?: string;
  bizNo?: string;
  matchStatus?: MatchStatus;
}

/** 分页查询同步日志 */
export function pageSyncLogs(query: SyncLogQuery): Promise<PageResult<SyncLog>> {
  return http.get<PageResult<SyncLog>>('/pdm/v1/integration/sync/page', { ...query });
}

/** 重试失败同步 */
export function retrySync(id: number): Promise<SyncLog> {
  return http.post<SyncLog>(`/pdm/v1/integration/sync/${id}/retry`);
}

/** 分页查询对账结果 */
export function pageReconcile(query: ReconcileQuery): Promise<PageResult<ReconcileRow>> {
  return http.get<PageResult<ReconcileRow>>('/pdm/v1/integration/reconcile/page', { ...query });
}

/** 执行 BOM 对账 */
export function runBomReconcile(): Promise<ReconcileRow[]> {
  return http.post<ReconcileRow[]>('/pdm/v1/integration/reconcile/bom/run');
}

/** 执行 MES 路线对账 */
export function runRouteReconcile(): Promise<ReconcileRow[]> {
  return http.post<ReconcileRow[]>('/pdm/v1/integration/reconcile/route/run');
}

/** 执行 OA 审批对账 */
export function runOaReconcile(): Promise<ReconcileRow[]> {
  return http.post<ReconcileRow[]>('/pdm/v1/integration/reconcile/oa/run');
}

/** 重新通知 ERP 拉取 BOM 快照 */
export function notifyBom(bomNo: string, versionNo: string): Promise<SyncLog> {
  return http.post<SyncLog>(`/pdm/v1/integration/reconcile/bom/${bomNo}/versions/${versionNo}/notify`);
}

/** 重推 MES 工艺路线 */
export function repushRoute(routeId: number): Promise<unknown> {
  return http.post<unknown>(`/pdm/v1/integration/reconcile/route/${routeId}/repush`);
}

/** OA 审批状态补偿 */
export function compensateOa(bizType: 'REQUEST' | 'ORDER', bizId: number): Promise<ReconcileRow> {
  return http.post<ReconcileRow>(`/pdm/v1/integration/reconcile/oa/${bizType}/${bizId}/compensate`);
}

/** 推送零件到 ERP */
export function syncPartToErp(partId: number): Promise<{
  erpItemId?: number;
  code: string;
  created: boolean;
  status: SyncStatus;
  lastError?: string;
}> {
  return http.post<{
    erpItemId?: number;
    code: string;
    created: boolean;
    status: SyncStatus;
    lastError?: string;
  }>('/pdm/v1/integration/erp/part/' + partId + '/sync');
}
