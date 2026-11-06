// 党政工会接口封装（M-P0 门禁 + M-P1 党建台账 + M-P2 党建内容）。
//
// 权限码与 migration-5.88-party-m0/m1/m2.sql 对齐：
//   sys:party:browse（1950）、sys:party:branch:list/manage（1951/1952）、
//   sys:party:member:list/manage（1954/1953）、sys:party:activity:list/manage（1955/1956）、
//   sys:party:fee:list/manage（1957/1958）、
//   sys:party:content:list/manage/audit（1959/1960/1961）。
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

// ---------------------------------------------------------------------
// M-P2 党建内容（先审后发）
// ---------------------------------------------------------------------

/** 党建内容视图 */
export interface ContentView {
  id: number;
  category: string;
  title: string;
  summary?: string | null;
  coverFileId?: number | null;
  body?: string | null;
  attachmentFileIds?: number[] | null;
  visibility: string;
  visibilityLabel: string;
  branchIds?: number[] | null;
  branchNames?: string[] | null;
  status: string;
  statusLabel: string;
  authorId: number;
  authorName?: string | null;
  submitTime?: string | null;
  publishedAt?: string | null;
  offlineAt?: string | null;
  readCount: number;
  remark?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 党建内容保存命令 */
export interface ContentSaveCommand {
  category: string;
  title: string;
  summary?: string | null;
  coverFileId?: number | null;
  body?: string | null;
  attachmentFileIds?: number[] | null;
  visibility?: string;
  branchIds?: number[] | null;
  remark?: string | null;
}

/** 党建内容管理查询条件 */
export interface ContentQuery {
  keyword?: string;
  category?: string;
  status?: string;
  visibility?: string;
  current?: number;
  size?: number;
}

/** 党建园地查询条件 */
export interface ContentPortalQuery {
  keyword?: string;
  category?: string;
  current?: number;
  size?: number;
}

/** 党建内容审核轨迹 */
export interface ContentAuditView {
  id: number;
  contentId: number;
  action: string;
  actionLabel: string;
  fromStatus?: string | null;
  toStatus?: string | null;
  opinion?: string | null;
  operatorId: number;
  operatorName?: string | null;
  createTime?: string | null;
}

/** 党建内容阅读记录 */
export interface ContentReadView {
  id: number;
  contentId: number;
  userId: number;
  userName?: string | null;
  readAt?: string | null;
}

/**
 * 党建内容管理分页（含未审稿，按权限与作者范围过滤）。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageContent(query: ContentQuery): Promise<PageResult<ContentView>> {
  return http.get<PageResult<ContentView>>('/party/content/page', { ...query });
}

/**
 * 党建内容管理详情。
 *
 * @param id 内容 ID
 * @returns 视图
 */
export function getContent(id: number): Promise<ContentView> {
  return http.get<ContentView>(`/party/content/${id}`);
}

/**
 * 新建党建内容（草稿）。
 *
 * @param command 保存命令
 * @returns 视图
 */
export function createContent(command: ContentSaveCommand): Promise<ContentView> {
  return http.post<ContentView>('/party/content', command);
}

/**
 * 编辑党建内容（已发布内容保存后自动退回待审）。
 *
 * @param id 内容 ID
 * @param command 保存命令
 * @returns 视图
 */
export function updateContent(id: number, command: ContentSaveCommand): Promise<ContentView> {
  return http.put<ContentView>(`/party/content/${id}`, command);
}

/**
 * 提交审核。
 *
 * @param id 内容 ID
 * @returns 视图
 */
export function submitContent(id: number): Promise<ContentView> {
  return http.post<ContentView>(`/party/content/${id}/submit`);
}

/**
 * 审核通过。
 *
 * @param id 内容 ID
 * @returns 视图
 */
export function approveContent(id: number): Promise<ContentView> {
  return http.post<ContentView>(`/party/content/${id}/approve`);
}

/**
 * 审核退回（意见必填）。
 *
 * @param id 内容 ID
 * @param opinion 审核意见
 * @returns 视图
 */
export function rejectContent(id: number, opinion: string): Promise<ContentView> {
  return http.post<ContentView>(`/party/content/${id}/reject`, { opinion });
}

/**
 * 下线（终态，保留阅读数）。
 *
 * @param id 内容 ID
 * @returns 视图
 */
export function offlineContent(id: number): Promise<ContentView> {
  return http.post<ContentView>(`/party/content/${id}/offline`);
}

/**
 * 审核轨迹。
 *
 * @param id 内容 ID
 * @returns 轨迹列表
 */
export function listContentAudits(id: number): Promise<ContentAuditView[]> {
  return http.get<ContentAuditView[]>(`/party/content/${id}/audits`);
}

/**
 * 党建园地阅读列表（仅已发布且当前用户可见）。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageContentPortal(query: ContentPortalQuery): Promise<PageResult<ContentView>> {
  return http.get<PageResult<ContentView>>('/party/content/portal/page', { ...query });
}

/**
 * 党建园地阅读详情。
 *
 * @param id 内容 ID
 * @returns 视图
 */
export function getContentPortal(id: number): Promise<ContentView> {
  return http.get<ContentView>(`/party/content/portal/${id}`);
}

/**
 * 登记阅读（同一人同一内容只计一次）。
 *
 * @param id 内容 ID
 * @returns 视图（含最新阅读人数）
 */
export function readContent(id: number): Promise<ContentView> {
  return http.post<ContentView>(`/party/content/${id}/read`);
}

/**
 * 阅读记录。
 *
 * @param id 内容 ID
 * @returns 阅读记录列表
 */
export function listContentReads(id: number): Promise<ContentReadView[]> {
  return http.get<ContentReadView[]>(`/party/content/${id}/reads`);
}

// ---------------------------------------------------------------------
// M-P3 工会：会员 / 慰问帮扶 / 节日福利 / 工会活动 / 职代会提案
// ---------------------------------------------------------------------

/** 工会会员视图 */
export interface UnionMemberView {
  id: number;
  memberNo: string;
  employeeNo: string;
  userId?: number | null;
  memberName: string;
  deptName?: string | null;
  joinDate: string;
  status: string;
  withdrawDate?: string | null;
  duesBase: number;
  duesPaidTo?: string | null;
  remark?: string | null;
  createTime?: string | null;
}

/** 工会会员保存命令 */
export interface UnionMemberSaveCommand {
  employeeNo: string;
  userId?: number | null;
  memberName: string;
  deptName?: string | null;
  joinDate: string;
  duesBase?: number | null;
  duesPaidTo?: string | null;
  remark?: string | null;
}

/** 工会会员查询条件 */
export interface UnionMemberQuery {
  keyword?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 慰问帮扶视图 */
export interface UnionReliefView {
  id: number;
  reliefNo: string;
  unionMemberId: number;
  applicantUserId?: number | null;
  reliefType: string;
  applyYear: number;
  amount: number;
  reason: string;
  status: string;
  approvalInstanceId?: number | null;
  approvalStatus?: string | null;
  approvalHint?: string | null;
  submitTime?: string | null;
  approvedAt?: string | null;
  paidAt?: string | null;
  remark?: string | null;
}

/** 慰问帮扶保存命令 */
export interface UnionReliefSaveCommand {
  unionMemberId: number;
  reliefType: string;
  amount: number;
  reason: string;
  remark?: string | null;
}

/** 慰问帮扶查询条件 */
export interface UnionReliefQuery {
  keyword?: string;
  status?: string;
  reliefType?: string;
  year?: number;
  current?: number;
  size?: number;
}

/** 慰问提交结果（approvalStarted=false 表示审批不可达，已存为草稿） */
export interface UnionReliefSubmitView {
  id: number;
  status: string;
  approvalStarted: boolean;
  approvalHint?: string | null;
  approvalInstanceId?: number | null;
}

/** 节日福利批次视图 */
export interface UnionWelfareView {
  id: number;
  welfareNo: string;
  title: string;
  welfareYear: number;
  standardAmount: number;
  status: string;
  issuedAt?: string | null;
  closedAt?: string | null;
  remark?: string | null;
  issueCount?: number | null;
}

/** 节日福利保存命令 */
export interface UnionWelfareSaveCommand {
  title: string;
  welfareYear: number;
  standardAmount: number;
  remark?: string | null;
}

/** 节日福利发放命令（memberIds 缺省或为空表示全量在册会员） */
export interface UnionWelfareIssueCommand {
  memberIds?: number[] | null;
}

/** 节日福利明细视图 */
export interface UnionWelfareItemView {
  id: number;
  welfareId: number;
  unionMemberId: number;
  employeeNo: string;
  memberName: string;
  amount: number;
  signStatus: string;
  signedAt?: string | null;
  welfareTitle?: string | null;
}

/** 节日福利查询条件 */
export interface UnionWelfareQuery {
  keyword?: string;
  year?: number;
  status?: string;
  current?: number;
  size?: number;
}

/** 节日福利明细分页条件 */
export interface UnionWelfareItemQuery {
  signStatus?: string;
  current?: number;
  size?: number;
}

/** 工会活动视图 */
export interface UnionActivityView {
  id: number;
  activityNo: string;
  title: string;
  activityType?: string | null;
  startTime: string;
  endTime: string;
  location?: string | null;
  quota: number;
  status: string;
  remark?: string | null;
  signupCount?: number | null;
}

/** 工会活动保存命令 */
export interface UnionActivitySaveCommand {
  title: string;
  activityType?: string | null;
  startTime: string;
  endTime: string;
  location?: string | null;
  quota?: number | null;
  remark?: string | null;
}

/** 工会活动报名记录 */
export interface UnionSignupView {
  id: number;
  activityId: number;
  activityTitle?: string | null;
  unionMemberId: number;
  memberName?: string | null;
  userId?: number | null;
  status: string;
  signupAt: string;
  cancelledAt?: string | null;
}

/** 工会活动查询条件 */
export interface UnionActivityQuery {
  keyword?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 工会活动园地查询条件 */
export interface UnionActivityPortalQuery {
  keyword?: string;
  current?: number;
  size?: number;
}

/** 工会活动报名记录分页条件 */
export interface UnionActivitySignupQuery {
  current?: number;
  size?: number;
}

/** 职代会提案视图 */
export interface UnionProposalView {
  id: number;
  proposalNo: string;
  title: string;
  content: string;
  submitterUserId?: number | null;
  submitterName?: string | null;
  status: string;
  handler?: string | null;
  filedAt?: string | null;
  handledAt?: string | null;
  feedback?: string | null;
  feedbackAt?: string | null;
  rating?: number | null;
  ratedAt?: string | null;
  createTime?: string | null;
}

/** 职代会提案提交命令 */
export interface UnionProposalSaveCommand {
  title: string;
  content: string;
}

/** 职代会提案查询条件 */
export interface UnionProposalQuery {
  keyword?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 我的提案查询条件 */
export interface UnionProposalPortalQuery {
  current?: number;
  size?: number;
}

/**
 * 工会会员分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionMembers(query: UnionMemberQuery): Promise<PageResult<UnionMemberView>> {
  return http.get<PageResult<UnionMemberView>>('/party/union/member/page', { ...query });
}

/**
 * 工会会员详情。
 *
 * @param id 会员 ID
 * @returns 视图
 */
export function getUnionMember(id: number): Promise<UnionMemberView> {
  return http.get<UnionMemberView>(`/party/union/member/${id}`);
}

/**
 * 办理入会。
 *
 * @param command 保存命令
 * @returns 会员视图
 */
export function createUnionMember(command: UnionMemberSaveCommand): Promise<UnionMemberView> {
  return http.post<UnionMemberView>('/party/union/member', command);
}

/**
 * 编辑会员档案（含会费基数与缴至月份）。
 *
 * @param id 会员 ID
 * @param command 保存命令
 * @returns 会员视图
 */
export function updateUnionMember(id: number, command: UnionMemberSaveCommand): Promise<UnionMemberView> {
  return http.put<UnionMemberView>(`/party/union/member/${id}`, command);
}

/**
 * 办理退会（终态，保留历史）。
 *
 * @param id 会员 ID
 * @param withdrawDate 退会日期（缺省为今天）
 * @returns 会员视图
 */
export function withdrawUnionMember(id: number, withdrawDate?: string): Promise<UnionMemberView> {
  return http.post<UnionMemberView>(`/party/union/member/${id}/withdraw`, { withdrawDate });
}

/**
 * 慰问帮扶分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionReliefs(query: UnionReliefQuery): Promise<PageResult<UnionReliefView>> {
  return http.get<PageResult<UnionReliefView>>('/party/union/relief/page', { ...query });
}

/**
 * 慰问帮扶详情。
 *
 * @param id 申请 ID
 * @returns 视图
 */
export function getUnionRelief(id: number): Promise<UnionReliefView> {
  return http.get<UnionReliefView>(`/party/union/relief/${id}`);
}

/**
 * 新建慰问申请。
 *
 * @param command 保存命令
 * @returns 申请视图
 */
export function createUnionRelief(command: UnionReliefSaveCommand): Promise<UnionReliefView> {
  return http.post<UnionReliefView>('/party/union/relief', command);
}

/**
 * 编辑慰问申请（仅草稿）。
 *
 * @param id 申请 ID
 * @param command 保存命令
 * @returns 申请视图
 */
export function updateUnionRelief(id: number, command: UnionReliefSaveCommand): Promise<UnionReliefView> {
  return http.put<UnionReliefView>(`/party/union/relief/${id}`, command);
}

/**
 * 提交审批（审批不可达时保存为草稿并返回 approvalStarted=false）。
 *
 * @param id 申请 ID
 * @returns 提交结果
 */
export function submitUnionRelief(id: number): Promise<UnionReliefSubmitView> {
  return http.post<UnionReliefSubmitView>(`/party/union/relief/${id}/submit`);
}

/**
 * 同步审批结果（仅 SUBMITTED）。
 *
 * @param id 申请 ID
 * @returns 申请视图
 */
export function syncUnionRelief(id: number): Promise<UnionReliefView> {
  return http.post<UnionReliefView>(`/party/union/relief/${id}/sync`);
}

/**
 * 登记发放（仅 APPROVED）。
 *
 * @param id 申请 ID
 * @returns 申请视图
 */
export function payUnionRelief(id: number): Promise<UnionReliefView> {
  return http.post<UnionReliefView>(`/party/union/relief/${id}/pay`);
}

/**
 * 节日福利批次分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionWelfares(query: UnionWelfareQuery): Promise<PageResult<UnionWelfareView>> {
  return http.get<PageResult<UnionWelfareView>>('/party/union/welfare/page', { ...query });
}

/**
 * 节日福利批次详情。
 *
 * @param id 批次 ID
 * @returns 视图
 */
export function getUnionWelfare(id: number): Promise<UnionWelfareView> {
  return http.get<UnionWelfareView>(`/party/union/welfare/${id}`);
}

/**
 * 新建福利批次。
 *
 * @param command 保存命令
 * @returns 视图
 */
export function createUnionWelfare(command: UnionWelfareSaveCommand): Promise<UnionWelfareView> {
  return http.post<UnionWelfareView>('/party/union/welfare', command);
}

/**
 * 编辑福利批次（仅草稿）。
 *
 * @param id 批次 ID
 * @param command 保存命令
 * @returns 视图
 */
export function updateUnionWelfare(id: number, command: UnionWelfareSaveCommand): Promise<UnionWelfareView> {
  return http.put<UnionWelfareView>(`/party/union/welfare/${id}`, command);
}

/**
 * 发放福利（名单为空表示全量在册会员，重复生成幂等）。
 *
 * @param id 批次 ID
 * @param command 发放名单
 * @returns 视图（issueCount 可能为空）
 */
export function issueUnionWelfare(id: number, command: UnionWelfareIssueCommand): Promise<UnionWelfareView> {
  return http.post<UnionWelfareView>(`/party/union/welfare/${id}/issue`, command);
}

/**
 * 福利明细分页。
 *
 * @param id 批次 ID
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionWelfareItems(
  id: number,
  query: UnionWelfareItemQuery,
): Promise<PageResult<UnionWelfareItemView>> {
  return http.get<PageResult<UnionWelfareItemView>>(`/party/union/welfare/${id}/items`, { ...query });
}

/**
 * 按人签收（幂等，仅已发放批次）。
 *
 * @param itemId 明细 ID
 * @returns 明细视图
 */
export function signUnionWelfareItem(itemId: number): Promise<UnionWelfareItemView> {
  return http.post<UnionWelfareItemView>(`/party/union/welfare/item/${itemId}/sign`);
}

/**
 * 关闭福利批次（终态，关闭后不可签收）。
 *
 * @param id 批次 ID
 * @returns 视图
 */
export function closeUnionWelfare(id: number): Promise<UnionWelfareView> {
  return http.post<UnionWelfareView>(`/party/union/welfare/${id}/close`);
}

/**
 * 我的福利分页（普通员工自助）。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageMyUnionWelfares(query: UnionWelfareItemQuery): Promise<PageResult<UnionWelfareItemView>> {
  return http.get<PageResult<UnionWelfareItemView>>('/party/union/welfare/my', { ...query });
}

/**
 * 工会活动管理分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionActivities(query: UnionActivityQuery): Promise<PageResult<UnionActivityView>> {
  return http.get<PageResult<UnionActivityView>>('/party/union/activity/page', { ...query });
}

/**
 * 工会活动详情。
 *
 * @param id 活动 ID
 * @returns 视图
 */
export function getUnionActivity(id: number): Promise<UnionActivityView> {
  return http.get<UnionActivityView>(`/party/union/activity/${id}`);
}

/**
 * 新建工会活动。
 *
 * @param command 保存命令
 * @returns 视图
 */
export function createUnionActivity(command: UnionActivitySaveCommand): Promise<UnionActivityView> {
  return http.post<UnionActivityView>('/party/union/activity', command);
}

/**
 * 编辑工会活动（发布后时间地点冻结，由后端校验）。
 *
 * @param id 活动 ID
 * @param command 保存命令
 * @returns 视图
 */
export function updateUnionActivity(id: number, command: UnionActivitySaveCommand): Promise<UnionActivityView> {
  return http.put<UnionActivityView>(`/party/union/activity/${id}`, command);
}

/**
 * 发布活动。
 *
 * @param id 活动 ID
 * @returns 视图
 */
export function publishUnionActivity(id: number): Promise<UnionActivityView> {
  return http.post<UnionActivityView>(`/party/union/activity/${id}/publish`);
}

/**
 * 关闭活动（终态）。
 *
 * @param id 活动 ID
 * @returns 视图
 */
export function closeUnionActivity(id: number): Promise<UnionActivityView> {
  return http.post<UnionActivityView>(`/party/union/activity/${id}/close`);
}

/**
 * 取消活动（终态）。
 *
 * @param id 活动 ID
 * @returns 视图
 */
export function cancelUnionActivity(id: number): Promise<UnionActivityView> {
  return http.post<UnionActivityView>(`/party/union/activity/${id}/cancel`);
}

/**
 * 活动报名名单分页（管理端）。
 *
 * @param id 活动 ID
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionActivitySignups(
  id: number,
  query: UnionActivitySignupQuery,
): Promise<PageResult<UnionSignupView>> {
  return http.get<PageResult<UnionSignupView>>(`/party/union/activity/${id}/signups`, { ...query });
}

/**
 * 活动园地分页（员工可见，仅已发布）。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionActivityPortal(
  query: UnionActivityPortalQuery,
): Promise<PageResult<UnionActivityView>> {
  return http.get<PageResult<UnionActivityView>>('/party/union/activity/portal/page', { ...query });
}

/**
 * 活动园地详情。
 *
 * @param id 活动 ID
 * @returns 视图
 */
export function getUnionActivityPortal(id: number): Promise<UnionActivityView> {
  return http.get<UnionActivityView>(`/party/union/activity/portal/${id}`);
}

/**
 * 员工报名。
 *
 * @param id 活动 ID
 * @returns 报名记录
 */
export function signupUnionActivity(id: number): Promise<UnionSignupView> {
  return http.post<UnionSignupView>(`/party/union/activity/portal/${id}/signup`);
}

/**
 * 员工取消报名。
 *
 * @param id 活动 ID
 * @returns 报名记录
 */
export function cancelUnionActivitySignup(id: number): Promise<UnionSignupView> {
  return http.post<UnionSignupView>(`/party/union/activity/portal/${id}/cancel`);
}

/**
 * 我的报名分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageMyUnionActivities(query: UnionActivitySignupQuery): Promise<PageResult<UnionSignupView>> {
  return http.get<PageResult<UnionSignupView>>('/party/union/activity/my', { ...query });
}

/**
 * 职代会提案管理分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionProposals(query: UnionProposalQuery): Promise<PageResult<UnionProposalView>> {
  return http.get<PageResult<UnionProposalView>>('/party/union/proposal/page', { ...query });
}

/**
 * 职代会提案详情。
 *
 * @param id 提案 ID
 * @returns 视图
 */
export function getUnionProposal(id: number): Promise<UnionProposalView> {
  return http.get<UnionProposalView>(`/party/union/proposal/${id}`);
}

/**
 * 提交提案（员工自助）。
 *
 * @param command 提案内容
 * @returns 视图
 */
export function createUnionProposal(command: UnionProposalSaveCommand): Promise<UnionProposalView> {
  return http.post<UnionProposalView>('/party/union/proposal', command);
}

/**
 * 立案。
 *
 * @param id 提案 ID
 * @param handler 承办人（可选）
 * @returns 视图
 */
export function fileUnionProposal(id: number, handler?: string): Promise<UnionProposalView> {
  return http.post<UnionProposalView>(`/party/union/proposal/${id}/file`, { handler });
}

/**
 * 办理（记录承办人）。
 *
 * @param id 提案 ID
 * @param handler 承办人
 * @returns 视图
 */
export function handleUnionProposal(id: number, handler: string): Promise<UnionProposalView> {
  return http.post<UnionProposalView>(`/party/union/proposal/${id}/handle`, { handler });
}

/**
 * 反馈办理结果。
 *
 * @param id 提案 ID
 * @param feedback 反馈内容
 * @returns 视图
 */
export function feedbackUnionProposal(id: number, feedback: string): Promise<UnionProposalView> {
  return http.post<UnionProposalView>(`/party/union/proposal/${id}/feedback`, { feedback });
}

/**
 * 评价（1–5 星，仅提交人本人且处于已反馈）。
 *
 * @param id 提案 ID
 * @param rating 评分
 * @returns 视图
 */
export function rateUnionProposal(id: number, rating: number): Promise<UnionProposalView> {
  return http.post<UnionProposalView>(`/party/union/proposal/${id}/rate`, { rating });
}

/**
 * 我的提案分页。
 *
 * @param query 查询条件
 * @returns 分页结果
 */
export function pageUnionProposalPortal(
  query: UnionProposalPortalQuery,
): Promise<PageResult<UnionProposalView>> {
  return http.get<PageResult<UnionProposalView>>('/party/union/proposal/portal/page', { ...query });
}

/**
 * 我的提案详情。
 *
 * @param id 提案 ID
 * @returns 视图
 */
export function getUnionProposalPortal(id: number): Promise<UnionProposalView> {
  return http.get<UnionProposalView>(`/party/union/proposal/portal/${id}`);
}
