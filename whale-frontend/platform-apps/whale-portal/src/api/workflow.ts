// workflow.ts —— 工作流待办 API 层
//
// 后端接口：GET /workflow/v1/task/page（经网关 8080 代理到 whale-workflow）。
// 查询失败（服务未启动 / 未授权 / 网络异常）时返回空数组并记录日志，保证门户页面可用。

import { createLogger } from '@bluewhale/whale-common-tools';

import { http } from './client';

const logger = createLogger('whale-portal › workflow');

/** 待办任务（字段对齐 backend ProcessTaskVO 冻结契约） */
export interface WorkflowTask {
  id: number;
  /** 所属流程实例 ID */
  instanceId: number;
  /** 审批节点下标（0 起） */
  nodeIndex: number;
  /** 节点名称 */
  nodeName: string;
  /** 节点键 */
  nodeKey: string;
  /** 节点绑定的表单定义 ID */
  formId?: string;
  /** 节点绑定的表单编码 */
  formCode?: string;
  /** 审批人账号 */
  assignee?: string;
  /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
  status: number;
  /** 审批意见 */
  comment?: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime?: string;
}

/** 分页查询入参 */
export interface WorkflowTaskQuery {
  pageNum?: number;
  pageSize?: number;
  /** 审批人账号 */
  assignee?: string;
  /** 任务状态 */
  status?: number;
  /** 流程实例 ID */
  instanceId?: number;
}

/** 后端分页结果信封（PageResult） */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

/** 将查询入参序列化为查询串（跳过空值） */
function buildQuery(params: WorkflowTaskQuery): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const queryString = search.toString();
  return queryString ? `?${queryString}` : '';
}

/**
 * 分页查询当前用户待办任务（含总数）。
 * 失败时返回空页并记录日志，保证页面可用。
 */
export async function pageMyTaskPage(
  params: WorkflowTaskQuery = {},
): Promise<PageResult<WorkflowTask>> {
  try {
    const page = await http.get<PageResult<WorkflowTask>>(
      `/workflow/v1/task/page${buildQuery(params)}`,
    );
    return {
      records: page?.records ?? [],
      total: page?.total ?? 0,
      size: page?.size ?? 0,
      current: page?.current ?? 0,
    };
  } catch (error) {
    logger.warn('查询待办任务失败，返回空列表', error);
    return { records: [], total: 0, size: 0, current: 0 };
  }
}

/**
 * 分页查询待办任务记录。
 * 失败时返回空数组并记录日志，保证页面可用。
 */
export async function pageMyTask(params: WorkflowTaskQuery = {}): Promise<WorkflowTask[]> {
  return (await pageMyTaskPage(params)).records;
}

/** 统计运行中的流程实例数（status=0），失败时抛错由调用方降级展示 */
export async function countRunningInstances(): Promise<number> {
  const page = await http.get<PageResult<unknown>>('/workflow/v1/instance/page?status=0&pageSize=1');
  return page?.total ?? 0;
}

/** 同意待办任务（可选审批意见） */
export async function approveTask(id: number, comment?: string): Promise<void> {
  await http.post<void>(`/workflow/v1/task/${id}/approve`, {
    comment: comment?.trim() || undefined,
  });
}

/** 驳回待办任务（可选审批意见） */
export async function rejectTask(id: number, comment?: string): Promise<void> {
  await http.post<void>(`/workflow/v1/task/${id}/reject`, {
    comment: comment?.trim() || undefined,
  });
}
