// 定时任务 API（经网关 → whale-job /job）

import { del, get, post, put, type PageResult } from './http';

export interface JobInfoVO {
  id: number;
  jobName: string;
  jobGroup?: string;
  cronExpression: string;
  handlerBean: string;
  handlerParam?: string;
  misfirePolicy?: string;
  status?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  nextFireTime?: string;
}

export interface JobLogVO {
  id: number;
  jobId: number;
  jobName?: string;
  handlerBean?: string;
  startTime?: string;
  endTime?: string;
  status?: number;
  errorMsg?: string;
  result?: string;
  createTime?: string;
}

/** 任务分页查询参数：keyword 模糊匹配任务名称，status 1 运行中 / 0 已停止 */
export interface JobInfoPageParams {
  keyword?: string;
  jobGroup?: string;
  status?: number;
  current: number;
  size: number;
}

/** 执行日志分页查询参数：status 1 成功 / 0 执行中 / -1 失败 */
export interface JobLogPageParams {
  jobId?: number;
  status?: number;
  current: number;
  size: number;
}

/** 新增/更新任务请求体 */
export interface JobPayload {
  jobName: string;
  jobGroup?: string;
  cronExpression: string;
  handlerBean: string;
  handlerParam?: string;
  /** 错过执行策略：SMART / FIRE_ONCE / DO_NOTHING */
  misfirePolicy?: string;
  status?: number;
  remark?: string;
}

export function pageJobInfo(params: JobInfoPageParams): Promise<PageResult<JobInfoVO>> {
  return get<PageResult<JobInfoVO>>('/job/info/page', { ...params });
}

export function createJob(body: JobPayload): Promise<void> {
  return post<void>('/job/info', body);
}

export function updateJob(id: number, body: JobPayload): Promise<void> {
  return put<void>(`/job/info/${id}`, body);
}

export function deleteJob(id: number): Promise<void> {
  return del<void>(`/job/info/${id}`);
}

export function changeJobStatus(id: number, status: number): Promise<void> {
  return put<void>(`/job/info/${id}/status`, { status });
}

export function runJob(id: number): Promise<void> {
  return post<void>(`/job/info/${id}/run`);
}

export function pageJobLog(params: JobLogPageParams): Promise<PageResult<JobLogVO>> {
  return get<PageResult<JobLogVO>>('/job/log/page', { ...params });
}
