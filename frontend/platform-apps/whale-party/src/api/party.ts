// 党政工会接口封装（M-P0 门禁 + M-P1 党建台账）。
//
// 权限码与 migration-5.88-party-m0/m1.sql 对齐：
//   sys:party:browse（1950）、sys:party:branch:list/manage（1951/1952）、
//   sys:party:member:list/manage（1954/1953）、sys:party:activity:list/manage（1955/1956）、
//   sys:party:fee:list/manage（1957/1958）。
// 路径前缀 `/party` 由网关注入，网关不剥离一级前缀（REQ-ARCH-003）。

import { http, type PageResult } from './client';

/** 门禁状态 */
export interface PartyMeta {
  demandGatePassed: boolean;
  message: string;
  milestones: string[];
  boundaries: string[];
}

/**
 * 查询党政工会门禁状态。
 *
 * @returns 门禁状态、里程碑与领域边界
 */
export function getMeta(): Promise<PartyMeta> {
  return http.get<PartyMeta>('/party/meta');
}

/** 支部视图 */
export interface BranchView {
  id: number;
  branchCode: string;
  branchName: string;
  parentId: number;
  parentName?: string | null;
  branchLevel: number;
  orgCode?: string | null;
  secretaryName?: string | null;
  secretaryUserId?: number | null;
  establishedDate?: string | null;
  status: string;
  statusLabel: string;
  sortNo?: number;
  remark?: string | null;
}

/** 支部树节点 */
export interface BranchTreeView {
  id: number;
  branchCode: string;
  branchName: string;
  parentId: number;
  branchLevel: number;
  orgCode?: string | null;
  secretaryName?: string | null;
  status: string;
  statusLabel: string;
  children: BranchTreeView[];
}

/** 支部保存命令 */
export interface BranchSaveCommand {
  branchName: string;
  parentId?: number | null;
  orgCode?: string | null;
  secretaryName?: string | null;
  secretaryUserId?: number | null;
  establishedDate?: string | null;
  sortNo?: number | null;
  remark?: string | null;
}

/** 支部查询条件 */
export interface BranchQuery {
  keyword?: string;
  status?: string;
  parentId?: number;
  current?: number;
  size?: number;
}

/**
 * 支部分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageBranch(query: BranchQuery): Promise<PageResult<BranchView>> {
  return http.get<PageResult<BranchView>>('/party/branch/page', { ...query });
}

/**
 * 支部树。
 *
 * @returns 树根列表
 */
export function branchTree(): Promise<BranchTreeView[]> {
  return http.get<BranchTreeView[]>('/party/branch/tree');
}

/**
 * 新建支部。
 *
 * @param command 保存命令
 * @returns 支部视图
 */
export function createBranch(command: BranchSaveCommand): Promise<BranchView> {
  return http.post<BranchView>('/party/branch', command);
}

/**
 * 编辑支部。
 *
 * @param id 支部 ID
 * @param command 保存命令
 * @returns 支部视图
 */
export function updateBranch(id: number, command: BranchSaveCommand): Promise<BranchView> {
  return http.put<BranchView>(`/party/branch/${id}`, command);
}

/**
 * 启用 / 停用支部。
 *
 * @param id 支部 ID
 * @param active 是否启用
 * @returns 支部视图
 */
export function changeBranchStatus(id: number, active: boolean): Promise<BranchView> {
  return http.post<BranchView>(`/party/branch/${id}/status?active=${active ? 'true' : 'false'}`);
}

/** 党员档案视图 */
export interface MemberView {
  id: number;
  memberNo: string;
  employeeNo: string;
  userId?: number | null;
  memberName: string;
  branchId: number;
  branchName?: string | null;
  politicalStatus: string;
  politicalLabel: string;
  joinPartyDate: string;
  fullMemberDate?: string | null;
  partyAgeYears: number;
  probationaryPending: boolean;
  duesBase: number;
  phone?: string | null;
  status: string;
  statusLabel: string;
  transferOutDate?: string | null;
  remark?: string | null;
}

/** 党员档案保存命令 */
export interface MemberSaveCommand {
  employeeNo: string;
  userId?: number | null;
  memberName: string;
  branchId: number;
  politicalStatus?: string;
  joinPartyDate: string;
  duesBase?: number | null;
  phone?: string | null;
  remark?: string | null;
}

/** 党员档案查询条件 */
export interface MemberQuery {
  keyword?: string;
  branchId?: number;
  politicalStatus?: string;
  status?: string;
  probationaryPending?: boolean;
  current?: number;
  size?: number;
}

/**
 * 党员档案分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageMember(query: MemberQuery): Promise<PageResult<MemberView>> {
  return http.get<PageResult<MemberView>>('/party/member/page', { ...query });
}

/**
 * 党员建档。
 *
 * @param command 保存命令
 * @returns 档案视图
 */
export function createMember(command: MemberSaveCommand): Promise<MemberView> {
  return http.post<MemberView>('/party/member', command);
}

/**
 * 编辑党员档案。
 *
 * @param id 档案 ID
 * @param command 保存命令
 * @returns 档案视图
 */
export function updateMember(id: number, command: MemberSaveCommand): Promise<MemberView> {
  return http.put<MemberView>(`/party/member/${id}`, command);
}

/**
 * 办理组织关系转出。
 *
 * @param id 档案 ID
 * @param transferDate 转出日期
 * @param remark 转出说明
 * @returns 档案视图
 */
export function transferMember(id: number, transferDate?: string, remark?: string): Promise<MemberView> {
  return http.post<MemberView>(`/party/member/${id}/transfer`, { transferDate, remark });
}

/**
 * 办理预备党员转正。
 *
 * @param id 档案 ID
 * @param fullMemberDate 转正日期
 * @returns 档案视图
 */
export function promoteMember(id: number, fullMemberDate?: string): Promise<MemberView> {
  const query = fullMemberDate ? `?fullMemberDate=${fullMemberDate}` : '';
  return http.post<MemberView>(`/party/member/${id}/promote${query}`);
}

/** 组织生活签到明细 */
export interface SigninView {
  memberId: number;
  memberName: string;
  present: boolean;
  absentReason?: string | null;
  signedAt?: string | null;
}

/** 组织生活视图 */
export interface ActivityView {
  id: number;
  branchId: number;
  branchName?: string | null;
  activityType: string;
  typeLabel: string;
  title: string;
  plannedDate: string;
  heldDate?: string | null;
  location?: string | null;
  hostName?: string | null;
  expectedCount: number;
  presentCount?: number | null;
  status: string;
  statusLabel: string;
  summary?: string | null;
  remark?: string | null;
  signins?: SigninView[] | null;
}

/** 组织生活保存命令 */
export interface ActivitySaveCommand {
  branchId: number;
  activityType?: string;
  title: string;
  plannedDate: string;
  location?: string | null;
  hostName?: string | null;
  expectedCount?: number | null;
  summary?: string | null;
  remark?: string | null;
}

/** 组织生活查询条件 */
export interface ActivityQuery {
  keyword?: string;
  branchId?: number;
  activityType?: string;
  status?: string;
  from?: string;
  to?: string;
  current?: number;
  size?: number;
}

/**
 * 组织生活分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageActivity(query: ActivityQuery): Promise<PageResult<ActivityView>> {
  return http.get<PageResult<ActivityView>>('/party/activity/page', { ...query });
}

/**
 * 组织生活详情（含签到明细）。
 *
 * @param id 组织生活 ID
 * @returns 视图
 */
export function getActivity(id: number): Promise<ActivityView> {
  return http.get<ActivityView>(`/party/activity/${id}`);
}

/**
 * 新建组织生活计划。
 *
 * @param command 保存命令
 * @returns 视图
 */
export function createActivity(command: ActivitySaveCommand): Promise<ActivityView> {
  return http.post<ActivityView>('/party/activity', command);
}

/**
 * 编辑组织生活。
 *
 * @param id 组织生活 ID
 * @param command 保存命令
 * @returns 视图
 */
export function updateActivity(id: number, command: ActivitySaveCommand): Promise<ActivityView> {
  return http.put<ActivityView>(`/party/activity/${id}`, command);
}

/**
 * 标记为已召开。
 *
 * @param id 组织生活 ID
 * @param heldDate 实际召开日期
 * @param summary 会议纪要
 * @returns 视图
 */
export function completeActivity(id: number, heldDate?: string, summary?: string): Promise<ActivityView> {
  const params = new URLSearchParams();
  if (heldDate) {
    params.set('heldDate', heldDate);
  }
  if (summary) {
    params.set('summary', summary);
  }
  const query = params.toString();
  return http.post<ActivityView>(`/party/activity/${id}/complete${query ? `?${query}` : ''}`);
}

/**
 * 归档组织生活。
 *
 * @param id 组织生活 ID
 * @returns 视图
 */
export function archiveActivity(id: number): Promise<ActivityView> {
  return http.post<ActivityView>(`/party/activity/${id}/archive`);
}

/**
 * 整场签到录入。
 *
 * @param id 组织生活 ID
 * @param items 签到明细
 * @returns 视图（含签到明细）
 */
export function signinActivity(
  id: number,
  items: Array<{ memberId: number; present: boolean; absentReason?: string }>,
): Promise<ActivityView> {
  return http.post<ActivityView>(`/party/activity/${id}/signin`, { items });
}

/** 党费档位视图 */
export interface FeeRuleView {
  id: number;
  ruleName: string;
  minBase: number;
  maxBase?: number | null;
  rate: number;
  ratePercent?: string | null;
  status: string;
  sortNo?: number;
  remark?: string | null;
}

/** 党费账单视图 */
export interface FeeBillView {
  id: number;
  memberId: number;
  memberName: string;
  branchId: number;
  branchName?: string | null;
  periodMonth: string;
  duesBase: number;
  rate: number;
  dueAmount: number;
  paidAmount: number;
  outstandingAmount: number;
  paidDate?: string | null;
  status: string;
  statusLabel: string;
  remark?: string | null;
}

/** 党费生成结果 */
export interface FeeGenerateResult {
  periodMonth: string;
  created: number;
  skipped: number;
  noRule: number;
  dueTotal: number;
  noRuleMembers: string[];
}

/** 党费账单查询条件 */
export interface FeeBillQuery {
  periodMonth?: string;
  branchId?: number;
  status?: string;
  keyword?: string;
  outstanding?: boolean;
  current?: number;
  size?: number;
}

/**
 * 党费档位列表。
 *
 * @returns 档位列表
 */
export function listFeeRules(): Promise<FeeRuleView[]> {
  return http.get<FeeRuleView[]>('/party/fee/rule');
}

/**
 * 初始化标准档位（幂等）。
 *
 * @returns 档位列表
 */
export function initFeeRules(): Promise<FeeRuleView[]> {
  return http.post<FeeRuleView[]>('/party/fee/rule/init');
}

/**
 * 党费账单分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageFeeBill(query: FeeBillQuery): Promise<PageResult<FeeBillView>> {
  return http.get<PageResult<FeeBillView>>('/party/fee/bill/page', { ...query });
}

/**
 * 按月份生成账单。
 *
 * @param periodMonth 所属月份 yyyyMM
 * @param branchId 支部 ID
 * @returns 生成结果
 */
export function generateFeeBills(periodMonth: string, branchId?: number): Promise<FeeGenerateResult> {
  const params = new URLSearchParams({ periodMonth });
  if (branchId) {
    params.set('branchId', String(branchId));
  }
  return http.post<FeeGenerateResult>(`/party/fee/bill/generate?${params.toString()}`);
}

/**
 * 登记缴费。
 *
 * @param billId 账单 ID
 * @param amount 缴纳金额
 * @param paidDate 缴费日期
 * @param remark 备注
 * @returns 账单视图
 */
export function payFeeBill(
  billId: number,
  amount: number,
  paidDate?: string,
  remark?: string,
): Promise<FeeBillView> {
  return http.post<FeeBillView>(`/party/fee/bill/${billId}/pay`, { amount, paidDate, remark });
}

/**
 * 标记免缴。
 *
 * @param billId 账单 ID
 * @param reason 免缴原因
 * @returns 账单视图
 */
export function waiveFeeBill(billId: number, reason?: string): Promise<FeeBillView> {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return http.post<FeeBillView>(`/party/fee/bill/${billId}/waive${query}`);
}
