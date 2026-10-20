// workflow.ts —— 工作流待办 API 层
//
// 后端接口：GET /workflow/task/page（经网关 8080 代理到 whale-workflow）。
// 查询失败（服务未启动 / 未授权 / 网络异常）时返回空数组并 console.warn，保证门户页面可用。

import { http } from './http';

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
interface PageResult<T> {
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
 * 分页查询当前用户待办任务。
 * 失败时返回空数组并 console.warn，保证页面可用。
 */
export async function pageMyTask(params: WorkflowTaskQuery = {}): Promise<WorkflowTask[]> {
  try {
    const page = await http.get<PageResult<WorkflowTask>>(
      `/workflow/task/page${buildQuery(params)}`,
    );
    return page?.records ?? [];
  } catch (error) {
    console.warn('[whale-portal] 查询待办任务失败，返回空列表', error);
    return [];
  }
}
