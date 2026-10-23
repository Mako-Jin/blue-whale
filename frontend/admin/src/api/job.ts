// 定时任务 API（经网关 → whale-job /job）

import { del, get, post, put, type PageResult } from './client';

export interface JobInfoVO {
  id: number;
  jobName: string;
  jobGroup?: string;
  cronExpression: string;
  handlerBean: string;
  handlerParam?: string;
  misfirePolicy?: string;
  /** 失败重试次数上限：0 不重试，上限 5 */
  maxRetry?: number;
  /** 重试间隔秒数，默认 60 */
  retryIntervalSeconds?: number;
  /** 分片总数：1 不分片，上限 16 */
  shardTotal?: number;
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
  /** 执行批次号 */
  runId?: string;
  /** 触发方式：CRON / MANUAL / CHAIN */
  triggerType?: string;
  createTime?: string;
}

/** 任务上下游依赖 */
export interface JobDependencyVO {
  jobId: number;
  upstreamJobIds: number[];
  downstreamJobIds: number[];
}

/** 任务失败告警记录 */
export interface JobAlertVO {
  id: number;
  jobId: number;
  jobName?: string;
  logId?: number;
  runId?: string;
  level?: string;
  channel?: string;
  /** 1 成功 / -1 失败 / 0 待发 */
  status?: number;
  message?: string;
  errorMsg?: string;
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

/** 告警记录分页查询参数：level 告警级别 */
export interface JobAlertPageParams {
  jobId?: number;
  level?: string;
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
  /** 失败重试次数上限：0 不重试，上限 5 */
  maxRetry?: number;
  /** 重试间隔秒数，默认 60 */
  retryIntervalSeconds?: number;
  /** 分片总数：1 不分片，上限 16 */
  shardTotal?: number;
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

export function getJobDependencies(id: number): Promise<JobDependencyVO> {
  return get<JobDependencyVO>(`/job/info/${id}/dependencies`);
}

/** 全量替换上游依赖 */
export function setJobDependencies(id: number, upstreamJobIds: number[]): Promise<void> {
  return post<void>(`/job/info/${id}/dependencies`, { upstreamJobIds });
}

export function pageJobAlert(params: JobAlertPageParams): Promise<PageResult<JobAlertVO>> {
  return get<PageResult<JobAlertVO>>('/job/alert/page', { ...params });
}
