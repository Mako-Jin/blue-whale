// OA 审批中心 API 层：四类列表、详情、动作、催办/撤销、代理设置与流程监控
//
// 字段对齐 backend whale-oa approval 包下的 view/command；流程实例与任务 ID 为 number。

import { http, type PageResult } from './client';

/** 审批中心分页查询入参 */
export interface ApprovalQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: number;
  definitionCode?: string;
}

/** 流程实例行视图（我发起 / 监控） */
export interface ApprovalInstanceRow {
  workflowInstanceId: number;
  definitionCode?: string;
  title?: string;
  status?: number;
  statusName?: string;
  currentNodeKey?: string;
  initiator?: string;
  createTime?: string;
  updateTime?: string;
  businessType?: string;
  businessTitle?: string;
  businessCode?: string;
  businessUrl?: string;
}

/** 审批任务行视图（待我审批 / 我已审批） */
export interface ApprovalTaskRow {
  id: number;
  instanceId: number;
  nodeKey?: string;
  nodeName?: string;
  assignee?: string;
  ownerId?: string;
  status?: number;
  comment?: string;
  createTime?: string;
  updateTime?: string;
  businessType?: string;
  businessTitle?: string;
  businessCode?: string;
  businessUrl?: string;
  instanceStatus?: string;
  initiatorName?: string;
  canOperate?: boolean;
}

/** 抄送我的行视图 */
export interface ApprovalCcRow {
  id: number;
  instanceId: number;
  nodeKey?: string;
  nodeName?: string;
  readFlag?: number;
  readTime?: string;
  createTime?: string;
  businessType?: string;
  businessTitle?: string;
  businessCode?: string;
  businessUrl?: string;
  instanceStatus?: string;
  initiatorName?: string;
}

/** 审批详情时间线任务 */
export interface ApprovalTimelineTask {
  id: number;
  nodeKey?: string;
  nodeName?: string;
  assignee?: string;
  ownerId?: string;
  delegateFrom?: string;
  status?: number;
  comment?: string;
  createTime?: string;
  updateTime?: string;
  canOperate?: boolean;
}

/** 业务详情字段 */
export interface ApprovalDetailField {
  label: string;
  value: string;
}

/** 业务详情（白名单） */
export interface ApprovalBusinessDetail {
  businessType?: string;
  businessId?: number;
  summary?: string;
  status?: string;
  url?: string;
  fields?: ApprovalDetailField[];
}

/** 审批详情 */
export interface ApprovalDetail {
  instance: ApprovalInstanceRow;
  tasks: ApprovalTimelineTask[];
  ccs: ApprovalCcRow[];
  businessDetail?: ApprovalBusinessDetail | null;
  canCancel?: boolean;
  canUrge?: boolean;
}

/** 代理规则 */
export interface ApprovalDelegateRule {
  id: number;
  definitionCode: string;
  fromAccount: string;
  toAccount: string;
  startTime?: string;
  endTime?: string;
  enabled?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 代理规则保存入参 */
export interface ApprovalDelegateRuleInput {
  definitionCode: string;
  toAccount: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  remark?: string;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== false) {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 我发起的审批分页 */
export function pageInitiated(
  params: ApprovalQuery = {},
): Promise<PageResult<ApprovalInstanceRow>> {
  return http.get<PageResult<ApprovalInstanceRow>>(
    `/oa/approval/center/initiated/page${buildQuery(params)}`,
  );
}

/** 待我审批分页 */
export function pagePending(params: ApprovalQuery = {}): Promise<PageResult<ApprovalTaskRow>> {
  return http.get<PageResult<ApprovalTaskRow>>(
    `/oa/approval/center/pending/page${buildQuery(params)}`,
  );
}

/** 我已审批分页 */
export function pageApproved(params: ApprovalQuery = {}): Promise<PageResult<ApprovalTaskRow>> {
  return http.get<PageResult<ApprovalTaskRow>>(
    `/oa/approval/center/approved/page${buildQuery(params)}`,
  );
}

/** 抄送我的分页 */
export function pageCc(params: ApprovalQuery = {}): Promise<PageResult<ApprovalCcRow>> {
  return http.get<PageResult<ApprovalCcRow>>(`/oa/approval/center/cc/page${buildQuery(params)}`);
}

/** 审批中心详情 */
export function getDetail(workflowInstanceId: number): Promise<ApprovalDetail> {
  return http.get<ApprovalDetail>(`/oa/approval/center/instance/${workflowInstanceId}`);
}

/** 同意任务 */
export function approveTask(
  workflowInstanceId: number,
  taskId: number,
  comment?: string,
): Promise<void> {
  return http.post<void>(
    `/oa/approval/center/instance/${workflowInstanceId}/task/${taskId}/approve`,
    { comment },
  );
}

/** 驳回任务 */
export function rejectTask(
  workflowInstanceId: number,
  taskId: number,
  comment?: string,
): Promise<void> {
  return http.post<void>(
    `/oa/approval/center/instance/${workflowInstanceId}/task/${taskId}/reject`,
    { comment },
  );
}

/** 转办任务 */
export function transferTask(
  workflowInstanceId: number,
  taskId: number,
  targetUser: string,
): Promise<void> {
  return http.post<void>(
    `/oa/approval/center/instance/${workflowInstanceId}/task/${taskId}/transfer`,
    { targetUser },
  );
}

/** 加签 */
export function addSign(
  workflowInstanceId: number,
  taskId: number,
  assignees: string[],
): Promise<void> {
  return http.post<void>(
    `/oa/approval/center/instance/${workflowInstanceId}/task/${taskId}/add-sign`,
    { assignees },
  );
}

/** 退回 */
export function rollbackTask(
  workflowInstanceId: number,
  taskId: number,
  targetNodeKey: string,
): Promise<void> {
  return http.post<void>(
    `/oa/approval/center/instance/${workflowInstanceId}/task/${taskId}/rollback`,
    { targetNodeKey },
  );
}

/** 催办 */
export function urgeInstance(
  workflowInstanceId: number,
  targetAccount?: string,
  comment?: string,
): Promise<void> {
  return http.post<void>(`/oa/approval/center/instance/${workflowInstanceId}/urge`, {
    targetAccount,
    comment,
  });
}

/** 撤销 */
export function cancelInstance(workflowInstanceId: number): Promise<void> {
  return http.post<void>(`/oa/approval/center/instance/${workflowInstanceId}/cancel`);
}

/** 标记抄送已读 */
export function markCcRead(ccId: number): Promise<void> {
  return http.post<void>(`/oa/approval/center/cc/${ccId}/read`);
}

/** 流程监控分页 */
export function pageMonitor(params: ApprovalQuery = {}): Promise<PageResult<ApprovalInstanceRow>> {
  return http.get<PageResult<ApprovalInstanceRow>>(
    `/oa/approval/monitor/page${buildQuery(params)}`,
  );
}

/** 流程监控详情（只读） */
export function getMonitorDetail(workflowInstanceId: number): Promise<ApprovalDetail> {
  return http.get<ApprovalDetail>(`/oa/approval/monitor/instance/${workflowInstanceId}`);
}

/** 本人代理规则分页 */
export function pageDelegateRules(
  current = 1,
  size = 10,
): Promise<PageResult<ApprovalDelegateRule>> {
  return http.get<PageResult<ApprovalDelegateRule>>(
    `/oa/approval/delegate/page${buildQuery({ current, size })}`,
  );
}

/** 新建代理规则 */
export function createDelegateRule(
  input: ApprovalDelegateRuleInput,
): Promise<ApprovalDelegateRule> {
  return http.post<ApprovalDelegateRule>('/oa/approval/delegate', input);
}

/** 修改代理规则 */
export function updateDelegateRule(
  id: number,
  input: ApprovalDelegateRuleInput,
): Promise<ApprovalDelegateRule> {
  return http.put<ApprovalDelegateRule>(`/oa/approval/delegate/${id}`, input);
}

/** 删除代理规则 */
export function deleteDelegateRule(id: number): Promise<void> {
  return http.del<void>(`/oa/approval/delegate/${id}`);
}
