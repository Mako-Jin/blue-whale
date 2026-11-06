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
