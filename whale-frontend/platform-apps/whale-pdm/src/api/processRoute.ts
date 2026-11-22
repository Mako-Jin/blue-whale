// 工艺路线 API：经网关 /pdm/** 转发到 whale-pdm
//
// 路线发布后不可编辑；下发 MES 失败可重试，撤回会同时作废路线与下发记录。

import { http, type PageResult } from './client';

/** 工艺路线状态 */
export type ProcessStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE';

/** MES 下发状态 */
export type ProcessPublishStatus = 'SUCCESS' | 'FAILED' | 'WITHDRAWN';

/** 工装类型 */
export type ToolingType = 'TOOL' | 'FIXTURE' | 'MOLD' | 'GAUGE';

/** 工艺工装 */
export interface ProcessTooling {
  id: string;
  stepId?: string;
  toolingType: ToolingType;
  toolingCode: string;
  spec?: string;
  quantity: number;
  remark?: string;
}

/** 工序 SOP */
export interface ProcessSop {
  id: string;
  stepId: string;
  documentId: string;
  documentVersionNo: string;
  documentTitle?: string;
}

/** 工艺工序 */
export interface ProcessStep {
  id: string;
  stepNo: number;
  name: string;
  workCenter?: string;
  standardHours: number;
  setupHours: number;
  description?: string;
  toolings: ProcessTooling[];
  sop?: ProcessSop;
}

/** MES 下发记录 */
export interface ProcessPublish {
  id: string;
  status: ProcessPublishStatus;
  mesRouteNo?: string;
  retryCount: number;
  lastError?: string;
  pushedAt?: string;
  withdrawnAt?: string;
}

/** 工艺路线 */
export interface ProcessRoute {
  id: string;
  routeNo: string;
  partId: string;
  partCode: string;
  partName?: string;
  versionNo: string;
  status: ProcessStatus;
  remark?: string;
  createdBy?: number;
  releasedBy?: number;
  releasedAt?: string;
  createTime?: string;
  updateTime?: string;
  steps: ProcessStep[];
  publish?: ProcessPublish;
}

/** 路线分页查询 */
export interface ProcessRouteQuery {
  current?: number;
  size?: number;
  routeNo?: string;
  partCode?: string;
  status?: ProcessStatus;
}

/** 路线新建入参 */
export interface ProcessRouteInput {
  partId: string;
  routeNo?: string;
  remark?: string;
}

/** 工序入参 */
export interface ProcessStepInput {
  stepNo?: number;
  name: string;
  workCenter?: string;
  standardHours?: number;
  setupHours?: number;
  description?: string;
}

/** 工装入参 */
export interface ProcessToolingInput {
  stepId?: string;
  toolingType: ToolingType;
  toolingCode: string;
  spec?: string;
  quantity?: number;
  remark?: string;
}

/** SOP 入参 */
export interface ProcessSopInput {
  stepId: string;
  documentId: string;
  documentVersionNo: string;
}

/** 路线分页查询 */
export function listProcessRoutes(params: ProcessRouteQuery): Promise<PageResult<ProcessRoute>> {
  return http.get<PageResult<ProcessRoute>>(
    '/pdm/v1/process/route/page',
    params as Record<string, unknown>,
  );
}

/** 路线详情 */
export function getProcessRoute(id: string): Promise<ProcessRoute> {
  return http.get<ProcessRoute>(`/pdm/v1/process/route/${id}`);
}

/** 新建路线版本 */
export function createProcessRoute(input: ProcessRouteInput): Promise<ProcessRoute> {
  return http.post<ProcessRoute>('/pdm/v1/process/route', input);
}

/** 更新草稿路线 */
export function updateProcessRoute(id: string, input: ProcessRouteInput): Promise<ProcessRoute> {
  return http.put<ProcessRoute>(`/pdm/v1/process/route/${id}`, input);
}

/** 删除草稿路线 */
export function deleteProcessRoute(id: string): Promise<void> {
  return http.del<void>(`/pdm/v1/process/route/${id}`);
}

/** 新增工序 */
export function addProcessStep(id: string, input: ProcessStepInput): Promise<ProcessRoute> {
  return http.post<ProcessRoute>(`/pdm/v1/process/route/${id}/steps`, input);
}

/** 更新工序 */
export function updateProcessStep(
  id: string,
  stepId: string,
  input: ProcessStepInput,
): Promise<ProcessRoute> {
  return http.put<ProcessRoute>(`/pdm/v1/process/route/${id}/steps/${stepId}`, input);
}

/** 删除工序 */
export function deleteProcessStep(id: string, stepId: string): Promise<ProcessRoute> {
  return http.del<ProcessRoute>(`/pdm/v1/process/route/${id}/steps/${stepId}`);
}

/** 新增工装 */
export function addProcessTooling(id: string, input: ProcessToolingInput): Promise<ProcessRoute> {
  return http.post<ProcessRoute>(`/pdm/v1/process/route/${id}/toolings`, input);
}

/** 删除工装 */
export function deleteProcessTooling(id: string, toolingId: string): Promise<ProcessRoute> {
  return http.del<ProcessRoute>(`/pdm/v1/process/route/${id}/toolings/${toolingId}`);
}

/** 保存工序 SOP */
export function saveProcessSop(id: string, input: ProcessSopInput): Promise<ProcessRoute> {
  return http.post<ProcessRoute>(`/pdm/v1/process/route/${id}/sop`, input);
}

/** 删除工序 SOP */
export function deleteProcessSop(id: string, sopId: string): Promise<ProcessRoute> {
  return http.del<ProcessRoute>(`/pdm/v1/process/route/${id}/sop/${sopId}`);
}

/** 发布路线并下发 MES */
export function releaseProcessRoute(id: string): Promise<ProcessRoute> {
  return http.post<ProcessRoute>(`/pdm/v1/process/route/${id}/release`);
}

/** 重试 MES 下发 */
export function retryProcessPublish(id: string): Promise<ProcessRoute> {
  return http.post<ProcessRoute>(`/pdm/v1/process/route/${id}/publish/retry`);
}

/** 撤回路线 */
export function withdrawProcessRoute(id: string): Promise<ProcessRoute> {
  return http.post<ProcessRoute>(`/pdm/v1/process/route/${id}/withdraw`);
}
