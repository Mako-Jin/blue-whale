// 客户关系 API 层：与 whale-crm 后端控制器一一对应
//
// 对应控制器：
// - CustomerController           /crm/customer
// - CustomerContactController    /crm/customer（联系人 / 地址子资源）
// - CustomerFollowUpController   /crm/follow-up
// - CustomerPoolController       /crm/pool
// - CrmMetaController            /crm/meta
//
// 后端统一分页返回 PageResult（records / total / size / current）。

import { http } from './client';
import type { PageResult } from './client';

/** 客户状态（后端 CustomerStatus） */
export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'MERGED';

/** 客户等级（后端 CustomerLevel） */
export type CustomerLevel = 'A' | 'B' | 'C' | 'D';

/** 公海状态（后端 PoolStatus） */
export type PoolStatus = 'OWNED' | 'IN_POOL';

/** 跟进渠道（后端 FollowUpChannel） */
export type FollowUpChannel = 'CALL' | 'VISIT' | 'EMAIL' | 'IM' | 'WECHAT' | 'OTHER';

/** 客户摘要视图（CustomerView） */
export interface CustomerView {
  id: number;
  customerCode?: string | null;
  name: string;
  shortName?: string | null;
  status: string;
  ownerId?: number | null;
  deptId?: number | null;
  level?: string | null;
  source?: string | null;
  industry?: string | null;
  address?: string | null;
  tags?: string | null;
  mergedIntoId?: number | null;
  website?: string | null;
  scale?: string | null;
  /** 按权限脱敏 */
  taxNo?: string | null;
  province?: string | null;
  city?: string | null;
  region?: string | null;
  poolStatus?: string | null;
  poolAt?: string | null;
  lastFollowUpAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 联系人视图（ContactView） */
export interface ContactView {
  id: number;
  customerId: number;
  name: string;
  /** 按权限脱敏 */
  phone?: string | null;
  /** 按权限脱敏 */
  email?: string | null;
  /** 按权限脱敏 */
  wechat?: string | null;
  title?: string | null;
  decisionRole?: string | null;
  decisionMaker?: boolean | null;
  primary?: boolean | null;
  status?: string | null;
}

/** 客户地址视图（CustomerAddressView） */
export interface CustomerAddressView {
  id: number;
  customerId: number;
  contactName?: string | null;
  /** 按权限脱敏 */
  phone?: string | null;
  province?: string | null;
  city?: string | null;
  district?: string | null;
  detail?: string | null;
  fullAddress?: string | null;
  defaultShipping?: boolean | null;
  defaultBilling?: boolean | null;
  status?: string | null;
}

/** 跟进记录视图（FollowUpView） */
export interface FollowUpView {
  id: number;
  customerId: number;
  opportunityId?: number | null;
  channel?: string | null;
  content?: string | null;
  result?: string | null;
  nextAction?: string | null;
  nextActionAt?: string | null;
  createdBy?: number | null;
  createTime?: string | null;
}

/** 决策链成员（DecisionChainEntry） */
export interface DecisionChainEntry {
  contactId?: number | null;
  role?: string | null;
  influence?: string | null;
  remark?: string | null;
}

/** 客户画像视图（CustomerProfileView） */
export interface CustomerProfileView {
  id?: number | null;
  customerId: number;
  preference?: string | null;
  painPoints?: string | null;
  decisionChain?: DecisionChainEntry[] | null;
  keyPerson?: string | null;
  /** 按权限脱敏 */
  sensitiveRemark?: string | null;
  version?: number | null;
}

/** 客户详情（CustomerDetailView） */
export interface CustomerDetailView {
  customer: CustomerView;
  contacts: ContactView[];
  addresses: CustomerAddressView[];
  profile: CustomerProfileView;
}

/** 商机摘要（CustomerOpportunitySummary） */
export interface CustomerOpportunitySummary {
  totalCount: number;
  totalAmount?: number | string | null;
  items?: {
    id?: number | null;
    name?: string | null;
    stage?: string | null;
    amount?: number | string | null;
  }[];
}

/** 负责人变更留痕（OwnerLogView） */
export interface OwnerLogView {
  id: number;
  customerId: number;
  action?: string | null;
  fromOwnerId?: number | null;
  toOwnerId?: number | null;
  operatorId?: number | null;
  remark?: string | null;
  createTime?: string | null;
}

/** 客户 360 视图（Customer360View） */
export interface Customer360View {
  customer: CustomerView;
  contacts: ContactView[];
  profile: CustomerProfileView;
  recentFollowUps: FollowUpView[];
  opportunitySummary: CustomerOpportunitySummary;
  ownerLogs: OwnerLogView[];
  unavailableIntegrations: string[];
  generatedAt?: string | null;
}

/** 查重候选（DuplicateCandidateView） */
export interface DuplicateCandidateView {
  id: number;
  customerCode?: string | null;
  name?: string | null;
  level?: string | null;
  ownerId?: number | null;
  /** HARD / SOFT */
  severity?: string | null;
  matchedFields?: string | null;
}

/** 公海规则视图（PoolRuleView） */
export interface PoolRuleView {
  id: number;
  ruleName?: string | null;
  inactiveDays?: number | null;
  noOpportunityDays?: number | null;
  levels?: string | null;
  deptId?: number | null;
  dailyClaimLimit?: number | null;
  enabled?: boolean | null;
  lastRunAt?: string | null;
  recycleCount?: number | null;
}

/** 公海回收结果（PoolRecycleResultView） */
export interface PoolRecycleResultView {
  recycledCount: number;
  ruleName?: string | null;
  executedAt?: string | null;
}

/** CRM 门禁状态（CrmMetaController.CrmMetaView） */
export interface CrmMetaView {
  demandGatePassed: boolean;
  message?: string | null;
  milestones: string[];
  boundaries: string[];
}

/**
 * 客户 / 公海分页查询参数（CustomerPageParams）。
 *
 * ownerId 在领域查询（CustomerQuery）中已存在，但当前 CustomerPageParams 未绑定该字段，
 * 服务端数据范围仍由登录人身份决定；此处保留参数以便后端补齐绑定。
 */
export interface CustomerPageQuery {
  keyword?: string;
  status?: string;
  level?: string;
  source?: string;
  industry?: string;
  ownerId?: number | null;
  current?: number;
  size?: number;
}

/** 客户新建 / 编辑命令（CustomerSaveCommand） */
export interface CustomerSaveInput {
  customerCode?: string;
  name: string;
  shortName?: string;
  source?: string;
  industry?: string;
  address?: string;
  tags?: string;
  website?: string;
  scale?: string;
  taxNo?: string;
  province?: string;
  city?: string;
  region?: string;
  level?: string;
  ownerId?: number | null;
  deptId?: number | null;
  confirmedDuplicate?: boolean;
}

/** 联系人新建 / 编辑命令（ContactSaveCommand） */
export interface ContactSaveInput {
  name: string;
  phone?: string;
  email?: string;
  wechat?: string;
  title?: string;
  decisionRole?: string;
  decisionMaker?: boolean;
  primary?: boolean;
  status?: string;
  confirmedDuplicate?: boolean;
}

/** 客户地址新建 / 编辑命令（AddressSaveCommand） */
export interface AddressSaveInput {
  contactName?: string;
  phone?: string;
  province?: string;
  city?: string;
  district?: string;
  detail: string;
  defaultShipping?: boolean;
  defaultBilling?: boolean;
  status?: string;
}

/** 跟进记录新增命令（FollowUpCreateCommand，append-only） */
export interface FollowUpCreateInput {
  customerId: number;
  opportunityId?: number | null;
  channel: string;
  content: string;
  result?: string;
  nextAction?: string;
  nextActionAt?: string | null;
}

/** 公海分配命令（PoolAssignCommand） */
export interface PoolAssignInput {
  ownerId: number;
  deptId?: number | null;
  remark?: string;
}

/** 公海规则保存命令（PoolRuleSaveCommand） */
export interface PoolRuleSaveInput {
  id?: number | null;
  ruleName?: string;
  inactiveDays?: number | null;
  noOpportunityDays?: number | null;
  levels?: string;
  deptId?: number | null;
  dailyClaimLimit?: number | null;
  enabled?: boolean;
}

/** 画像保存命令（ProfileSaveCommand） */
export interface ProfileSaveInput {
  preference?: string;
  painPoints?: string;
  decisionChain?: DecisionChainEntry[];
  keyPerson?: string;
  sensitiveRemark?: string;
}

/** 客户分页 */
export function pageCustomers(query: CustomerPageQuery): Promise<PageResult<CustomerView>> {
  return http.get<PageResult<CustomerView>>('/crm/customer/page', { ...query });
}

/** 客户查重 */
export function duplicateCheck(params: {
  name?: string;
  website?: string;
  taxNo?: string;
  phone?: string;
  email?: string;
  excludeId?: number;
}): Promise<PageResult<DuplicateCandidateView>> {
  return http.get<PageResult<DuplicateCandidateView>>('/crm/customer/duplicate-check', {
    ...params,
  });
}

/** 客户详情（档案 + 联系人 + 地址 + 画像） */
export function getCustomerDetail(id: number): Promise<CustomerDetailView> {
  return http.get<CustomerDetailView>(`/crm/customer/${id}`);
}

/** 客户 360 视图 */
export function getCustomer360(id: number): Promise<Customer360View> {
  return http.get<Customer360View>(`/crm/customer/${id}/360`);
}

/** 新建客户 */
export function createCustomer(input: CustomerSaveInput): Promise<CustomerView> {
  return http.post<CustomerView>('/crm/customer', input);
}

/** 编辑客户 */
export function updateCustomer(id: number, input: CustomerSaveInput): Promise<CustomerView> {
  return http.put<CustomerView>(`/crm/customer/${id}`, input);
}

/** 变更客户状态（ACTIVE / INACTIVE） */
export function changeCustomerStatus(id: number, status: string): Promise<CustomerView> {
  return http.put<CustomerView>(`/crm/customer/${id}/status`, { status });
}

/** 变更客户等级（A/B/C/D） */
export function changeCustomerLevel(id: number, level: string): Promise<CustomerView> {
  return http.put<CustomerView>(`/crm/customer/${id}/level`, { level });
}

/** 逻辑删除客户 */
export function deleteCustomer(id: number): Promise<void> {
  return http.del<void>(`/crm/customer/${id}`);
}

/** 查询客户画像 */
export function getCustomerProfile(id: number): Promise<CustomerProfileView> {
  return http.get<CustomerProfileView>(`/crm/customer/${id}/profile`);
}

/** 保存客户画像 */
export function saveCustomerProfile(
  id: number,
  input: ProfileSaveInput,
): Promise<CustomerProfileView> {
  return http.put<CustomerProfileView>(`/crm/customer/${id}/profile`, input);
}

/** 查询客户联系人 */
export function listContacts(customerId: number): Promise<ContactView[]> {
  return http.get<ContactView[]>(`/crm/customer/${customerId}/contact`);
}

/** 新建联系人 */
export function createContact(
  customerId: number,
  input: ContactSaveInput,
): Promise<ContactView> {
  return http.post<ContactView>(`/crm/customer/${customerId}/contact`, input);
}

/** 编辑联系人 */
export function updateContact(
  contactId: number,
  input: ContactSaveInput,
): Promise<ContactView> {
  return http.put<ContactView>(`/crm/customer/contact/${contactId}`, input);
}

/** 删除联系人 */
export function deleteContact(contactId: number): Promise<void> {
  return http.del<void>(`/crm/customer/contact/${contactId}`);
}

/** 查询客户地址 */
export function listAddresses(customerId: number): Promise<CustomerAddressView[]> {
  return http.get<CustomerAddressView[]>(`/crm/customer/${customerId}/address`);
}

/** 新建客户地址 */
export function createAddress(
  customerId: number,
  input: AddressSaveInput,
): Promise<CustomerAddressView> {
  return http.post<CustomerAddressView>(`/crm/customer/${customerId}/address`, input);
}

/** 编辑客户地址 */
export function updateAddress(
  addressId: number,
  input: AddressSaveInput,
): Promise<CustomerAddressView> {
  return http.put<CustomerAddressView>(`/crm/customer/address/${addressId}`, input);
}

/** 删除客户地址 */
export function deleteAddress(addressId: number): Promise<void> {
  return http.del<void>(`/crm/customer/address/${addressId}`);
}

/** 客户跟进分页 */
export function pageFollowUps(
  customerId: number,
  current: number,
  size: number,
): Promise<PageResult<FollowUpView>> {
  return http.get<PageResult<FollowUpView>>('/crm/follow-up/page', {
    customerId,
    current,
    size,
  });
}

/** 新增跟进记录 */
export function createFollowUp(input: FollowUpCreateInput): Promise<FollowUpView> {
  return http.post<FollowUpView>('/crm/follow-up', input);
}

/** 公海分页 */
export function pagePoolCustomers(query: CustomerPageQuery): Promise<PageResult<CustomerView>> {
  return http.get<PageResult<CustomerView>>('/crm/pool/page', { ...query });
}

/** 领取公海客户 */
export function claimPoolCustomer(id: number): Promise<CustomerView> {
  return http.post<CustomerView>(`/crm/pool/${id}/claim`);
}

/** 释放客户到公海（remark 为后端 @RequestParam） */
export function releasePoolCustomer(id: number, remark?: string): Promise<CustomerView> {
  return http.post<CustomerView>(`/crm/pool/${id}/release`, undefined, { remark });
}

/** 分配客户负责人 */
export function assignPoolCustomer(
  id: number,
  input: PoolAssignInput,
): Promise<CustomerView> {
  return http.post<CustomerView>(`/crm/pool/${id}/assign`, input);
}

/** 按规则回收客户到公海 */
export function recyclePool(): Promise<PoolRecycleResultView> {
  return http.post<PoolRecycleResultView>('/crm/pool/recycle');
}

/** 查询公海规则 */
export function getPoolRule(): Promise<PoolRuleView> {
  return http.get<PoolRuleView>('/crm/pool/rule');
}

/** 保存公海规则 */
export function savePoolRule(input: PoolRuleSaveInput): Promise<PoolRuleView> {
  return http.put<PoolRuleView>('/crm/pool/rule', input);
}

/** 查询客户负责人变更历史 */
export function listOwnerLogs(id: number): Promise<OwnerLogView[]> {
  return http.get<OwnerLogView[]>(`/crm/pool/${id}/owner-logs`);
}

/** 查询 CRM 门禁状态 */
export function getCrmMeta(): Promise<CrmMetaView> {
  return http.get<CrmMetaView>('/crm/meta');
}

// ---------------------------------------------------------------------------
// 销售域（线索 / 商机 / 报价 / 预测 / 目标）
//
// 对应控制器：
// - LeadController          /crm/lead
// - OpportunityController   /crm/opportunity
// - QuoteController         /crm/quote
// - ForecastController      /crm/forecast
// - SalesTargetController   /crm/sales-target
// ---------------------------------------------------------------------------

/** 线索状态（后端 LeadStatus） */
export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'CONVERTED' | 'INVALID';

/** 商机阶段（后端 OpportunityStage，兼容存量 LEAD / QUALIFIED） */
export type OpportunityStage = 'CONTACT' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST';

/** 报价状态（后端 QuoteStatus） */
export type QuoteStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

/** 预测 / 目标周期类型（后端 ForecastPeriodType） */
export type ForecastPeriodType = 'MONTH' | 'QUARTER';

/** 预测 / 目标统计范围（后端 ForecastScopeType） */
export type ForecastScopeType = 'USER' | 'DEPT';

/** 线索视图（LeadView） */
export interface LeadView {
  id: number;
  leadNo?: string | null;
  name: string;
  source?: string | null;
  industry?: string | null;
  region?: string | null;
  contactName?: string | null;
  /** 按权限脱敏 */
  contactPhone?: string | null;
  /** 按权限脱敏 */
  contactEmail?: string | null;
  status?: string | null;
  invalidReason?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  customerId?: number | null;
  opportunityId?: number | null;
  convertedAt?: string | null;
  remark?: string | null;
  createTime?: string | null;
}

/** 线索活动视图（LeadActivityView） */
export interface LeadActivityView {
  id: number;
  leadId: number;
  channel?: string | null;
  content?: string | null;
  result?: string | null;
  nextActionAt?: string | null;
  createdBy?: number | null;
  createTime?: string | null;
}

/** 线索转化结果（LeadConvertResultView） */
export interface LeadConvertResultView {
  leadId: number;
  customerId?: number | null;
  opportunityId?: number | null;
  convertedAt?: string | null;
  /** true 表示返回既有转化结果，未新建 */
  alreadyConverted: boolean;
}

/** 线索分页查询参数（LeadQuery） */
export interface LeadPageQuery {
  keyword?: string;
  status?: string;
  source?: string;
  ownerId?: number | null;
  current?: number;
  size?: number;
}

/** 线索新建 / 编辑命令（LeadSaveCommand） */
export interface LeadSaveInput {
  name: string;
  source?: string;
  industry?: string;
  region?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  ownerId?: number | null;
  deptId?: number | null;
  remark?: string;
  confirmedDuplicate?: boolean;
}

/** 线索分配命令（LeadAssignCommand） */
export interface LeadAssignInput {
  ownerId: number;
  deptId?: number | null;
}

/** 线索转化命令（LeadConvertCommand） */
export interface LeadConvertInput {
  /** 既有客户 ID；为空则按线索信息新建客户 */
  customerId?: number | null;
  opportunityName?: string;
  amount?: number | null;
  expectedCloseDate?: string;
}

/** 线索活动新增命令（LeadActivityCreateCommand） */
export interface LeadActivityCreateInput {
  channel: string;
  content: string;
  result?: string;
  nextActionAt?: string;
}

/** 线索分页 */
export function pageLeads(query: LeadPageQuery): Promise<PageResult<LeadView>> {
  return http.get<PageResult<LeadView>>('/crm/lead/page', { ...query });
}

/** 线索查重（电话 / 邮箱） */
export function checkLeadDuplicates(params: {
  phone?: string;
  email?: string;
}): Promise<LeadView[]> {
  return http.get<LeadView[]>('/crm/lead/duplicate-check', { ...params });
}

/** 线索详情 */
export function getLeadDetail(id: number): Promise<LeadView> {
  return http.get<LeadView>(`/crm/lead/${id}`);
}

/** 新建线索 */
export function createLead(input: LeadSaveInput): Promise<LeadView> {
  return http.post<LeadView>('/crm/lead', input);
}

/** 编辑线索 */
export function updateLead(id: number, input: LeadSaveInput): Promise<LeadView> {
  return http.put<LeadView>(`/crm/lead/${id}`, input);
}

/** 推进线索状态（仅 CONTACTED / QUALIFIED） */
export function advanceLeadStatus(id: number, status: string): Promise<LeadView> {
  return http.put<LeadView>(`/crm/lead/${id}/status`, { status });
}

/** 分配线索负责人 */
export function assignLead(id: number, input: LeadAssignInput): Promise<LeadView> {
  return http.post<LeadView>(`/crm/lead/${id}/assign`, input);
}

/** 标记线索无效 */
export function invalidateLead(id: number, reason: string): Promise<LeadView> {
  return http.post<LeadView>(`/crm/lead/${id}/invalid`, { reason });
}

/** 线索转化（未指定 customerId 时按线索信息新建客户） */
export function convertLead(id: number, input: LeadConvertInput): Promise<LeadConvertResultView> {
  return http.post<LeadConvertResultView>(`/crm/lead/${id}/convert`, input);
}

/** 线索活动分页 */
export function pageLeadActivities(
  id: number,
  current: number,
  size: number,
): Promise<PageResult<LeadActivityView>> {
  return http.get<PageResult<LeadActivityView>>(`/crm/lead/${id}/activity`, { current, size });
}

/** 追加线索活动 */
export function addLeadActivity(
  id: number,
  input: LeadActivityCreateInput,
): Promise<LeadActivityView> {
  return http.post<LeadActivityView>(`/crm/lead/${id}/activity`, input);
}

/** 商机视图（OpportunityView） */
export interface OpportunityView {
  id: number;
  customerId?: number | null;
  name: string;
  amount?: number | string | null;
  stage?: string | null;
  probability?: number | string | null;
  source?: string | null;
  competitor?: string | null;
  nextActionAt?: string | null;
  stageEnteredAt?: string | null;
  expectedCloseDate?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  lostReason?: string | null;
  closedAt?: string | null;
  createTime?: string | null;
}

/** 商机阶段历史视图（OpportunityStageHistoryView） */
export interface OpportunityStageHistoryView {
  id: number;
  opportunityId: number;
  fromStage?: string | null;
  toStage?: string | null;
  amount?: number | string | null;
  probability?: number | string | null;
  lostReason?: string | null;
  operatorId?: number | null;
  changedAt?: string | null;
}

/** 漏斗阶段视图（FunnelStageView） */
export interface FunnelStageView {
  stage?: string | null;
  count: number;
  amount?: number | string | null;
  /** 相对上一阶段的转化率（百分比） */
  conversionRate?: number | string | null;
}

/** 销售漏斗视图（FunnelView） */
export interface FunnelView {
  stages: FunnelStageView[];
  lostCount: number;
  lostAmount?: number | string | null;
  /** 在途商机总数（不含输单） */
  totalCount: number;
  totalAmount?: number | string | null;
}

/** 商机分页 / 漏斗查询参数（OpportunityQuery） */
export interface OpportunityPageQuery {
  keyword?: string;
  customerId?: number | null;
  stage?: string;
  ownerId?: number | null;
  expectedCloseFrom?: string;
  expectedCloseTo?: string;
  current?: number;
  size?: number;
}

/** 商机新建 / 编辑命令（OpportunitySaveCommand） */
export interface OpportunitySaveInput {
  /** 新建必填；编辑时后端不接受更换客户 */
  customerId?: number | null;
  name: string;
  amount?: number | null;
  expectedCloseDate?: string;
  source?: string;
  competitor?: string;
  nextActionAt?: string;
  ownerId?: number | null;
  deptId?: number | null;
  probability?: number | null;
}

/** 商机阶段流转命令（OpportunityStageCommand） */
export interface OpportunityStageInput {
  stage: string;
  /** 转 LOST 必填 */
  lostReason?: string;
  /** 本次流转顺带更新金额（转 WON 可用） */
  amount?: number | null;
}

/** 商机分页 */
export function pageOpportunities(
  query: OpportunityPageQuery,
): Promise<PageResult<OpportunityView>> {
  return http.get<PageResult<OpportunityView>>('/crm/opportunity/page', { ...query });
}

/** 销售漏斗 */
export function getOpportunityFunnel(query: {
  ownerId?: number | null;
  expectedCloseFrom?: string;
  expectedCloseTo?: string;
}): Promise<FunnelView> {
  return http.get<FunnelView>('/crm/opportunity/funnel', { ...query });
}

/** 商机详情 */
export function getOpportunity(id: number): Promise<OpportunityView> {
  return http.get<OpportunityView>(`/crm/opportunity/${id}`);
}

/** 商机阶段历史 */
export function listOpportunityStageHistory(
  id: number,
): Promise<OpportunityStageHistoryView[]> {
  return http.get<OpportunityStageHistoryView[]>(`/crm/opportunity/${id}/history`);
}

/** 新建商机 */
export function createOpportunity(input: OpportunitySaveInput): Promise<OpportunityView> {
  return http.post<OpportunityView>('/crm/opportunity', input);
}

/** 编辑商机 */
export function updateOpportunity(
  id: number,
  input: OpportunitySaveInput,
): Promise<OpportunityView> {
  return http.put<OpportunityView>(`/crm/opportunity/${id}`, input);
}

/** 商机阶段流转 */
export function changeOpportunityStage(
  id: number,
  input: OpportunityStageInput,
): Promise<OpportunityView> {
  return http.post<OpportunityView>(`/crm/opportunity/${id}/stage`, input);
}

/** 报价行视图（QuoteLineView） */
export interface QuoteLineView {
  id: number;
  lineNo?: number | null;
  productName?: string | null;
  specification?: string | null;
  unit?: string | null;
  quantity?: number | string | null;
  unitPrice?: number | string | null;
  discountRate?: number | string | null;
  taxRate?: number | string | null;
  lineAmount?: number | string | null;
  remark?: string | null;
}

/** 报价视图（QuoteView） */
export interface QuoteView {
  id: number;
  quoteNo?: string | null;
  version?: number | null;
  rootQuoteId?: number | null;
  parentQuoteId?: number | null;
  opportunityId?: number | null;
  customerId?: number | null;
  title: string;
  status?: string | null;
  currency?: string | null;
  validUntil?: string | null;
  subtotalAmount?: number | string | null;
  taxAmount?: number | string | null;
  discountAmount?: number | string | null;
  totalAmount?: number | string | null;
  discountRate?: number | string | null;
  remark?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  approvalInstanceId?: number | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  lines?: QuoteLineView[] | null;
}

/** 报价分页查询参数（QuoteQuery） */
export interface QuotePageQuery {
  keyword?: string;
  opportunityId?: number | null;
  customerId?: number | null;
  status?: string;
  ownerId?: number | null;
  current?: number;
  size?: number;
}

/** 报价行命令（QuoteLineCommand） */
export interface QuoteLineInput {
  productName: string;
  specification?: string;
  unit?: string;
  quantity: number;
  unitPrice: number;
  discountRate?: number | null;
  taxRate?: number | null;
  remark?: string;
}

/** 报价保存命令（QuoteSaveCommand） */
export interface QuoteSaveInput {
  opportunityId: number;
  title: string;
  currency?: string;
  validUntil?: string;
  discountAmount?: number | null;
  remark?: string;
  lines: QuoteLineInput[];
}

/** 报价分页 */
export function pageQuotes(query: QuotePageQuery): Promise<PageResult<QuoteView>> {
  return http.get<PageResult<QuoteView>>('/crm/quote/page', { ...query });
}

/** 报价详情（含报价行） */
export function getQuote(id: number): Promise<QuoteView> {
  return http.get<QuoteView>(`/crm/quote/${id}`);
}

/** 新建报价 */
export function createQuote(input: QuoteSaveInput): Promise<QuoteView> {
  return http.post<QuoteView>('/crm/quote', input);
}

/** 编辑草稿报价 */
export function updateQuote(id: number, input: QuoteSaveInput): Promise<QuoteView> {
  return http.put<QuoteView>(`/crm/quote/${id}`, input);
}

/** 提交报价审批 */
export function submitQuote(id: number): Promise<QuoteView> {
  return http.post<QuoteView>(`/crm/quote/${id}/submit`);
}

/** 改价（生成新版本草稿） */
export function reviseQuote(id: number): Promise<QuoteView> {
  return http.post<QuoteView>(`/crm/quote/${id}/revise`);
}

/** 销售预测快照视图（ForecastSnapshotView） */
export interface ForecastSnapshotView {
  id: number;
  period?: string | null;
  periodType?: string | null;
  scopeType?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  pipelineAmount?: number | string | null;
  bestAmount?: number | string | null;
  commitAmount?: number | string | null;
  weightedAmount?: number | string | null;
  opportunityCount?: number | null;
  manualAdjusted?: boolean | null;
  remark?: string | null;
  createdBy?: number | null;
  updateTime?: string | null;
}

/** 预测调整记录视图（ForecastAdjustmentView） */
export interface ForecastAdjustmentView {
  id: number;
  snapshotId: number;
  fieldCode?: string | null;
  beforeAmount?: number | string | null;
  afterAmount?: number | string | null;
  reason?: string | null;
  operatorId?: number | null;
  adjustedAt?: string | null;
}

/** 预测分页查询参数（ForecastQuery） */
export interface ForecastPageQuery {
  period?: string;
  periodType?: string;
  scopeType?: string;
  ownerId?: number | null;
  deptId?: number | null;
  current?: number;
  size?: number;
}

/** 生成预测快照命令（ForecastGenerateCommand） */
export interface ForecastGenerateInput {
  /** yyyy-MM 或 yyyy-Qn */
  period: string;
  periodType?: string;
  scopeType?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 预测手工调整命令（ForecastAdjustCommand） */
export interface ForecastAdjustInput {
  /** PIPELINE / BEST / COMMIT */
  fieldCode: string;
  amount: number;
  reason: string;
}

/** 预测快照分页 */
export function pageForecasts(
  query: ForecastPageQuery,
): Promise<PageResult<ForecastSnapshotView>> {
  return http.get<PageResult<ForecastSnapshotView>>('/crm/forecast/page', { ...query });
}

/** 预测快照详情 */
export function getForecast(id: number): Promise<ForecastSnapshotView> {
  return http.get<ForecastSnapshotView>(`/crm/forecast/${id}`);
}

/** 预测调整记录 */
export function listForecastAdjustments(id: number): Promise<ForecastAdjustmentView[]> {
  return http.get<ForecastAdjustmentView[]>(`/crm/forecast/${id}/adjustments`);
}

/** 生成 / 重算预测快照 */
export function generateForecast(input: ForecastGenerateInput): Promise<ForecastSnapshotView> {
  return http.post<ForecastSnapshotView>('/crm/forecast/generate', input);
}

/** 手工调整预测金额 */
export function adjustForecast(
  id: number,
  input: ForecastAdjustInput,
): Promise<ForecastSnapshotView> {
  return http.put<ForecastSnapshotView>(`/crm/forecast/${id}/adjust`, input);
}

/** 销售目标视图（SalesTargetView） */
export interface SalesTargetView {
  id: number;
  period?: string | null;
  periodType?: string | null;
  scopeType?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  targetAmount?: number | string | null;
  remark?: string | null;
  createdBy?: number | null;
  lockVersion?: number | null;
  createTime?: string | null;
}

/** 目标调整记录视图（SalesTargetAdjustmentView） */
export interface SalesTargetAdjustmentView {
  id: number;
  targetId: number;
  beforeAmount?: number | string | null;
  afterAmount?: number | string | null;
  reason?: string | null;
  operatorId?: number | null;
  adjustedAt?: string | null;
}

/** 目标完成情况视图（TargetCompletionView） */
export interface TargetCompletionView {
  targetId: number;
  period?: string | null;
  periodType?: string | null;
  scopeType?: string | null;
  targetAmount?: number | string | null;
  achievedAmount?: number | string | null;
  /** 完成率（百分比） */
  completionRate?: number | string | null;
  achievedCount?: number | null;
}

/** 目标分页查询参数（SalesTargetQuery） */
export interface SalesTargetPageQuery {
  period?: string;
  periodType?: string;
  scopeType?: string;
  ownerId?: number | null;
  deptId?: number | null;
  current?: number;
  size?: number;
}

/** 新建销售目标命令（SalesTargetSaveCommand） */
export interface SalesTargetSaveInput {
  period: string;
  periodType?: string;
  scopeType?: string;
  ownerId?: number | null;
  deptId?: number | null;
  targetAmount: number;
  remark?: string;
}

/** 调整销售目标命令（SalesTargetUpdateCommand） */
export interface SalesTargetUpdateInput {
  targetAmount: number;
  reason?: string;
  remark?: string;
}

/** 销售目标分页 */
export function pageSalesTargets(
  query: SalesTargetPageQuery,
): Promise<PageResult<SalesTargetView>> {
  return http.get<PageResult<SalesTargetView>>('/crm/sales-target/page', { ...query });
}

/** 销售目标详情 */
export function getSalesTarget(id: number): Promise<SalesTargetView> {
  return http.get<SalesTargetView>(`/crm/sales-target/${id}`);
}

/** 销售目标完成情况 */
export function getSalesTargetCompletion(id: number): Promise<TargetCompletionView> {
  return http.get<TargetCompletionView>(`/crm/sales-target/${id}/completion`);
}

/** 销售目标调整记录 */
export function listSalesTargetAdjustments(
  id: number,
): Promise<SalesTargetAdjustmentView[]> {
  return http.get<SalesTargetAdjustmentView[]>(`/crm/sales-target/${id}/adjustments`);
}

/** 新建销售目标 */
export function createSalesTarget(input: SalesTargetSaveInput): Promise<SalesTargetView> {
  return http.post<SalesTargetView>('/crm/sales-target', input);
}

/** 调整销售目标（周期与范围不可变更） */
export function updateSalesTarget(
  id: number,
  input: SalesTargetUpdateInput,
): Promise<SalesTargetView> {
  return http.put<SalesTargetView>(`/crm/sales-target/${id}`, input);
}

/** 删除销售目标 */
export function deleteSalesTarget(id: number): Promise<void> {
  return http.del<void>(`/crm/sales-target/${id}`);
}
