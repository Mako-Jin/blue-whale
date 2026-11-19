// 定时任务 API（经网关 → whale-job /job）

import { del, downloadBlob, get, post, put, type PageResult } from './client';

export interface JobInfoVO {
  /** 后端 Long 序列化为字符串（雪花 ID） */
  id: string;
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
  /** 后端 Long 序列化为字符串 */
  id: string;
  /** 后端 Long 序列化为字符串 */
  jobId: string;
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
  /** 后端 Long 序列化为字符串 */
  jobId: string;
  /** 后端 Long 序列化为字符串数组 */
  upstreamJobIds: string[];
  /** 后端 Long 序列化为字符串数组 */
  downstreamJobIds: string[];
}

/** 任务失败告警记录 */
export interface JobAlertVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  /** 后端 Long 序列化为字符串 */
  jobId: string;
  jobName?: string;
  /** 后端 Long 序列化为字符串 */
  logId?: string;
  runId?: string;
  level?: string;
  /** 多通道以逗号拼接，如 DINGTALK,FEISHU；LOG 表示仅本地日志 */
  channel?: string;
  /** 是否恢复通知：level=INFO 的记录即「任务已恢复」 */
  recovered?: boolean;
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
  /** 后端 Long 序列化为字符串 */
  jobId?: string;
  status?: number;
  current: number;
  size: number;
}

/** 告警记录分页查询参数：level 精确匹配，channel 模糊匹配（单个通道名） */
export interface JobAlertPageParams {
  /** 后端 Long 序列化为字符串 */
  jobId?: string;
  level?: string;
  channel?: string;
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

export function updateJob(id: string, body: JobPayload): Promise<void> {
  return put<void>(`/job/info/${id}`, body);
}

export function deleteJob(id: string): Promise<void> {
  return del<void>(`/job/info/${id}`);
}

export function changeJobStatus(id: string, status: number): Promise<void> {
  return put<void>(`/job/info/${id}/status`, { status });
}

export function runJob(id: string): Promise<void> {
  return post<void>(`/job/info/${id}/run`);
}

export function pageJobLog(params: JobLogPageParams): Promise<PageResult<JobLogVO>> {
  return get<PageResult<JobLogVO>>('/job/log/page', { ...params });
}

export function getJobDependencies(id: string): Promise<JobDependencyVO> {
  return get<JobDependencyVO>(`/job/info/${id}/dependencies`);
}

/** 全量替换上游依赖 */
export function setJobDependencies(id: string, upstreamJobIds: string[]): Promise<void> {
  return post<void>(`/job/info/${id}/dependencies`, { upstreamJobIds });
}

export function pageJobAlert(params: JobAlertPageParams): Promise<PageResult<JobAlertVO>> {
  return get<PageResult<JobAlertVO>>('/job/alert/page', { ...params });
}

/** DAG 拓扑图节点（后端精简字段，不含执行参数） */
export interface JobGraphNodeVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  jobName: string;
  jobGroup?: string;
  /** 1 运行中 / 0 已停止 */
  status?: number;
  executorApp?: string;
  jobCode?: string;
  cronExpression?: string;
  shardTotal?: number;
  timeoutSeconds?: number;
  failThreshold?: number;
  failCount?: number;
}

/** DAG 依赖边：方向为上游 → 下游 */
export interface JobGraphEdgeVO {
  /** 后端 Long 序列化为字符串 */
  from: string;
  /** 后端 Long 序列化为字符串 */
  to: string;
}

export interface JobGraphVO {
  nodes: JobGraphNodeVO[];
  edges: JobGraphEdgeVO[];
}

/** 整图环检测结果：cycleJobIds 首尾为同一任务 ID，无环时为空数组 */
export interface JobGraphValidateVO {
  acyclic: boolean;
  /** 后端 Long 序列化为字符串数组 */
  cycleJobIds: string[];
}

/** 批量操作中单条失败明细 */
export interface JobBatchFailureVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  reason: string;
}

/** 批量启停 / 批量删除结果：整体 200，逐条失败在 failures 中 */
export interface JobBatchResultVO {
  successCount: number;
  failures: JobBatchFailureVO[];
}

/** 可移植的任务定义（导出/导入单项，不含 id / 租户 / 运行态计数） */
export interface JobTransferJob {
  jobName: string;
  jobGroup?: string;
  executorApp?: string;
  jobCode?: string;
  cronExpression: string;
  handlerBean: string;
  handlerParam?: string;
  misfirePolicy?: string;
  maxRetry?: number;
  retryIntervalSeconds?: number;
  shardTotal?: number;
  timeoutSeconds?: number;
  failThreshold?: number;
  status?: number;
  remark?: string;
}

/** 依赖边端点：用业务标识而非 ID，跨环境可解析 */
export interface JobTransferRef {
  executorApp?: string;
  jobCode?: string;
  jobName?: string;
}

export interface JobTransferDependency {
  /** 上游任务引用 */
  from: JobTransferRef;
  /** 下游任务引用 */
  to: JobTransferRef;
}

/** 导出文件结构：jobs / dependencies 可直接回灌导入接口 */
export interface JobExportVO {
  version: string;
  exportedAt?: string;
  jobs: JobTransferJob[];
  dependencies: JobTransferDependency[];
}

/** 导入请求：冲突策略 SKIP（默认）/ OVERWRITE */
export interface JobImportRequest {
  conflictStrategy: 'SKIP' | 'OVERWRITE';
  jobs: JobTransferJob[];
  dependencies?: JobTransferDependency[];
}

/** 导入单条失败明细：target 优先 executorApp:jobCode，否则任务名 */
export interface JobImportFailureVO {
  target: string;
  reason: string;
}

export interface JobImportResultVO {
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  dependencyCount: number;
  failures: JobImportFailureVO[];
}

/** 依赖拓扑图：jobGroup 为空返回全量 */
export function getJobGraph(jobGroup?: string): Promise<JobGraphVO> {
  return get<JobGraphVO>('/job/graph', { jobGroup });
}

/** 整图环检测（只读，供保存前校验与数据体检使用） */
export function validateJobGraph(): Promise<JobGraphValidateVO> {
  return get<JobGraphValidateVO>('/job/graph/validate');
}

/** 批量启停：逐条处理，部分失败通过 failures 返回 */
export function batchChangeJobStatus(jobIds: string[], status: number): Promise<JobBatchResultVO> {
  return post<JobBatchResultVO>('/job/info/batch-status', { jobIds, status });
}

/** 批量删除：逻辑删除，逐条处理 */
export function batchDeleteJobs(jobIds: string[]): Promise<JobBatchResultVO> {
  return post<JobBatchResultVO>('/job/info/batch-delete', { jobIds });
}

/** 导出任务定义并直接保存为 JSON 附件；未指定 jobIds 时导出整个分组 */
export async function downloadJobExport(params: {
  jobGroup?: string;
  jobIds?: string[];
}): Promise<void> {
  const { blob, filename } = await downloadBlob('/job/info/export/download', {
    jobGroup: params.jobGroup,
    jobIds: params.jobIds?.length ? params.jobIds.join(',') : undefined,
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

/** 导入任务定义与依赖边：单条失败不影响其余条目 */
export function importJobs(body: JobImportRequest): Promise<JobImportResultVO> {
  return post<JobImportResultVO>('/job/info/import', body);
}
