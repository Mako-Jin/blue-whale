// CRM 营销管理 API 层（M5）：活动 / 人群 / 培育 / 批量触达 / 渠道伙伴与报单
//
// 对应控制器：
// - CrmCampaignController    /crm/campaign
// - CrmNurtureController     /crm/nurture
// - CrmMarketingSendController /crm/marketing-send
// - CrmPartnerController     /crm/partner
//
// 触达实际投递走 whale-message；同一活动同一联系人在同一渠道只发一次，后端幂等拦截。

import { http } from './client';
import type { PageResult } from './client';

/** 活动类型 */
export type CampaignType = 'EXHIBITION' | 'LIVE' | 'PROMOTION' | 'OTHER';

/** 活动状态 */
export type CampaignStatus = 'DRAFT' | 'RUNNING' | 'ENDED' | 'CANCELLED';

/** 触达渠道 */
export type SendChannel = 'EMAIL' | 'SMS' | 'INBOX';

/** 触达任务状态 */
export type SendStatus = 'DRAFT' | 'EXECUTING' | 'COMPLETED' | 'FAILED';

/** 培育任务状态 */
export type NurtureStatus = 'OPEN' | 'DONE' | 'CANCELLED';

/** 渠道伙伴状态 */
export type PartnerStatus = 'ENABLED' | 'DISABLED';

/** 渠道报单状态 */
export type PartnerReportStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CONFLICT';

/** 活动视图 */
export interface CampaignView {
  id: number;
  campaignCode: string;
  campaignName: string;
  campaignType: CampaignType | string;
  status: CampaignStatus | string;
  channel?: string | null;
  budget?: number | null;
  actualCost?: number | null;
  plannedStart?: string | null;
  plannedEnd?: string | null;
  audienceId?: number | null;
  audienceCount?: number | null;
  attributedLeadCount?: number | null;
  attributedWonAmount?: number | null;
  roi?: number | null;
  ownerId?: number | null;
  deptId?: number | null;
  createTime?: string | null;
}

/** 活动筛选 */
export interface CampaignQuery {
  keyword?: string;
  status?: string;
  campaignType?: string;
  channel?: string;
  current?: number;
  size?: number;
}

/** 新建 / 编辑活动入参 */
export interface CampaignSaveInput {
  campaignName: string;
  campaignType: string;
  channel?: string;
  budget?: number | null;
  plannedStart?: string | null;
  plannedEnd?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 人群条件 */
export interface AudienceCriteria {
  industries?: string[];
  regions?: string[];
  levels?: string[];
  sources?: string[];
  tags?: string[];
  lastFollowUpDays?: number | null;
  ownerId?: number | null;
  excludeUnsubscribed?: boolean;
}

/** 人群预览 */
export interface AudiencePreviewView {
  count: number;
  samples: Array<{
    customerId: number;
    customerName: string;
    level?: string | null;
    industry?: string | null;
    source?: string | null;
  }>;
  criteriaJson?: string | null;
}

/** 人群快照视图 */
export interface CampaignAudienceView {
  id: number;
  campaignId: number;
  snapshotNo: string;
  criteriaJson?: string | null;
  memberCount?: number | null;
  locked?: boolean | null;
  lockedAt?: string | null;
}

/** 培育任务视图 */
export interface NurtureTaskView {
  id: number;
  taskNo: string;
  customerId: number;
  customerName?: string | null;
  leadId?: number | null;
  contentDocId?: number | null;
  contentDocVersion?: number | null;
  channel?: string | null;
  planDate?: string | null;
  status: NurtureStatus | string;
  assigneeId?: number | null;
  assigneeName?: string | null;
  note?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  createTime?: string | null;
}

/** 培育任务筛选 */
export interface NurtureQuery {
  keyword?: string;
  status?: string;
  customerId?: number | null;
  assigneeId?: number | null;
  current?: number;
  size?: number;
}

/** 培育任务入参 */
export interface NurtureSaveInput {
  customerId: number;
  leadId?: number | null;
  contentDocId?: number | null;
  contentDocVersion?: number | null;
  channel?: string;
  planDate?: string | null;
  assigneeId?: number | null;
  assigneeName?: string;
  note?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 触达明细 */
export interface MarketingSendItemView {
  id: number;
  customerId?: number | null;
  contactId?: number | null;
  contactName?: string | null;
  recipient?: string | null;
  channel?: string | null;
  status: string;
  error?: string | null;
  sentAt?: string | null;
}

/** 触达任务视图 */
export interface MarketingSendView {
  id: number;
  sendNo: string;
  campaignId: number;
  audienceId?: number | null;
  channel?: string | null;
  templateCode?: string | null;
  subject?: string | null;
  contentRefDocId?: number | null;
  status: SendStatus | string;
  totalCount?: number | null;
  sentCount?: number | null;
  skippedCount?: number | null;
  failedCount?: number | null;
  executedAt?: string | null;
  lastError?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  createTime?: string | null;
  items?: MarketingSendItemView[];
}

/** 触达筛选 */
export interface MarketingSendQuery {
  keyword?: string;
  campaignId?: number | null;
  status?: string;
  channel?: string;
  current?: number;
  size?: number;
}

/** 创建触达任务入参 */
export interface MarketingSendCreateInput {
  campaignId: number;
  audienceId?: number | null;
  channel: string;
  templateCode?: string;
  subject?: string;
  contentRefDocId?: number | null;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 渠道伙伴视图 */
export interface PartnerView {
  id: number;
  partnerCode: string;
  partnerName: string;
  partnerType?: string | null;
  level?: string | null;
  region?: string | null;
  contactName?: string | null;
  contactPhone?: string | null;
  status: PartnerStatus | string;
  ownerId?: number | null;
  deptId?: number | null;
  createTime?: string | null;
}

/** 渠道伙伴筛选 */
export interface PartnerQuery {
  keyword?: string;
  partnerType?: string;
  status?: string;
  region?: string;
  current?: number;
  size?: number;
}

/** 渠道伙伴入参 */
export interface PartnerSaveInput {
  partnerCode: string;
  partnerName: string;
  partnerType?: string;
  level?: string;
  region?: string;
  contactName?: string;
  contactPhone?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 渠道报单视图 */
export interface PartnerReportView {
  id: number;
  reportNo: string;
  partnerId: number;
  partnerName?: string | null;
  customerId?: number | null;
  customerName?: string | null;
  projectName?: string | null;
  estimatedAmount?: number | null;
  status: PartnerReportStatus | string;
  conflictReason?: string | null;
  approvedAmount?: number | null;
  resolvedBy?: number | null;
  resolvedAt?: string | null;
  ownerId?: number | null;
  deptId?: number | null;
  createTime?: string | null;
}

/** 渠道报单筛选 */
export interface PartnerReportQuery {
  keyword?: string;
  partnerId?: number | null;
  customerId?: number | null;
  status?: string;
  current?: number;
  size?: number;
}

/** 渠道报单入参 */
export interface PartnerReportCreateInput {
  partnerId: number;
  customerId: number;
  projectName?: string;
  estimatedAmount?: number | null;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 分页查询活动 */
export function pageCampaigns(query: CampaignQuery): Promise<PageResult<CampaignView>> {
  return http.get<PageResult<CampaignView>>('/crm/campaign/page', query as Record<string, unknown>);
}

/** 活动详情 */
export function getCampaign(id: number): Promise<CampaignView> {
  return http.get<CampaignView>(`/crm/campaign/${id}`);
}

/** 新建活动 */
export function createCampaign(input: CampaignSaveInput): Promise<CampaignView> {
  return http.post<CampaignView>('/crm/campaign', input);
}

/** 编辑活动 */
export function updateCampaign(id: number, input: CampaignSaveInput): Promise<CampaignView> {
  return http.put<CampaignView>(`/crm/campaign/${id}`, input);
}

/** 变更活动状态 */
export function changeCampaignStatus(id: number, status: string): Promise<CampaignView> {
  return http.post<CampaignView>(`/crm/campaign/${id}/status`, { status });
}

/** 回填活动 ROI */
export function updateCampaignRoi(
  id: number,
  input: { attributedLeadCount?: number; attributedWonAmount?: number; actualCost?: number },
): Promise<CampaignView> {
  return http.post<CampaignView>(`/crm/campaign/${id}/roi`, input);
}

/** 人群预览 */
export function previewAudience(
  id: number,
  criteria: AudienceCriteria,
): Promise<AudiencePreviewView> {
  return http.post<AudiencePreviewView>(`/crm/campaign/${id}/audience/preview`, { criteria });
}

/** 锁定人群快照 */
export function lockAudience(
  id: number,
  criteria: AudienceCriteria,
): Promise<CampaignAudienceView> {
  return http.post<CampaignAudienceView>(`/crm/campaign/${id}/audience/lock`, { criteria });
}

/** 分页查询培育任务 */
export function pageNurture(query: NurtureQuery): Promise<PageResult<NurtureTaskView>> {
  return http.get<PageResult<NurtureTaskView>>(
    '/crm/nurture/page',
    query as Record<string, unknown>,
  );
}

/** 新建培育任务 */
export function createNurture(input: NurtureSaveInput): Promise<NurtureTaskView> {
  return http.post<NurtureTaskView>('/crm/nurture', input);
}

/** 编辑培育任务 */
export function updateNurture(id: number, input: NurtureSaveInput): Promise<NurtureTaskView> {
  return http.put<NurtureTaskView>(`/crm/nurture/${id}`, input);
}

/** 变更培育任务状态 */
export function changeNurtureStatus(id: number, status: string): Promise<NurtureTaskView> {
  return http.post<NurtureTaskView>(`/crm/nurture/${id}/status`, { status });
}

/** 分页查询触达任务 */
export function pageSends(query: MarketingSendQuery): Promise<PageResult<MarketingSendView>> {
  return http.get<PageResult<MarketingSendView>>(
    '/crm/marketing-send/page',
    query as Record<string, unknown>,
  );
}

/** 触达任务详情 */
export function getSend(id: number): Promise<MarketingSendView> {
  return http.get<MarketingSendView>(`/crm/marketing-send/${id}`);
}

/** 创建触达任务 */
export function createSend(input: MarketingSendCreateInput): Promise<MarketingSendView> {
  return http.post<MarketingSendView>('/crm/marketing-send', input);
}

/** 执行触达任务 */
export function executeSend(id: number): Promise<MarketingSendView> {
  return http.post<MarketingSendView>(`/crm/marketing-send/${id}/execute`);
}

/** 分页查询渠道伙伴 */
export function pagePartners(query: PartnerQuery): Promise<PageResult<PartnerView>> {
  return http.get<PageResult<PartnerView>>('/crm/partner/page', query as Record<string, unknown>);
}

/** 新建渠道伙伴 */
export function createPartner(input: PartnerSaveInput): Promise<PartnerView> {
  return http.post<PartnerView>('/crm/partner', input);
}

/** 编辑渠道伙伴 */
export function updatePartner(id: number, input: PartnerSaveInput): Promise<PartnerView> {
  return http.put<PartnerView>(`/crm/partner/${id}`, input);
}

/** 分页查询渠道报单 */
export function pagePartnerReports(
  query: PartnerReportQuery,
): Promise<PageResult<PartnerReportView>> {
  return http.get<PageResult<PartnerReportView>>(
    '/crm/partner/report/page',
    query as Record<string, unknown>,
  );
}

/** 新建渠道报单 */
export function createPartnerReport(input: PartnerReportCreateInput): Promise<PartnerReportView> {
  return http.post<PartnerReportView>('/crm/partner/report', input);
}

/** 裁决渠道报单冲突 */
export function resolvePartnerReport(
  id: number,
  input: { status: string; approvedAmount?: number | null },
): Promise<PartnerReportView> {
  return http.post<PartnerReportView>(`/crm/partner/report/${id}/resolve`, input);
}
