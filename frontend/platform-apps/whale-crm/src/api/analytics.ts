// CRM 数据分析 API 层（M6）：看板 / 漏斗 / 客户 / 排行 / 回款 / 指标快照 / 行动计划 / 导出
//
// 全量只读投影 + 规则化建议；不做数据仓库、不写其他上下文的业务表。
// 指标口径：赢单额按商机 WON 且关闭时间落在区间；合同/订单/开票取台账；
// 回款取回款计划已收金额；应收逾期取最新快照；CSAT 取满意度均分。

import { download, http } from './client';
import type { PageResult } from './client';

/** 指标快照 */
export interface MetricSnapshot {
  id: number;
  metricCode: string;
  statDate: string;
  dimensionType: string;
  dimensionId?: string | null;
  metricValue?: number | null;
  metricExtra?: string | null;
}

/** 销售看板 */
export interface DashboardData {
  statDate?: string | null;
  todayNewLeads: number;
  weekWonAmount?: number | null;
  monthTargetAmount?: number | null;
  monthWonAmount?: number | null;
  targetCompletionRate?: number | null;
  weightedPipelineAmount?: number | null;
  forecastGap?: number | null;
  riskOpportunityCount: number;
  openActionCount: number;
}

/** 漏斗阶段 */
export interface FunnelStage {
  stage: string;
  stageName?: string | null;
  count: number;
  amount?: number | null;
  conversionRate?: number | null;
  avgStayDays?: number | null;
}

/** 漏斗视图 */
export interface FunnelViewData {
  startDate?: string | null;
  endDate?: string | null;
  stages: FunnelStage[];
  lostCount: number;
  lostAmount?: number | null;
  totalCount: number;
  totalAmount?: number | null;
}

/** 客户分析 */
export interface CustomerAnalysisData {
  statDate?: string | null;
  newCount: number;
  activeCount: number;
  levelDistribution: Record<string, number>;
  industryDistribution: Record<string, number>;
  sourceDistribution: Record<string, number>;
  regionDistribution: Record<string, number>;
  top5Concentration?: number | null;
}

/** 销售排行行 */
export interface RankingRow {
  rank: number;
  ownerId?: number | null;
  ownerName?: string | null;
  wonAmount?: number | null;
  wonCount: number;
  contractCount: number;
  collectionRate?: number | null;
}

/** 回款分析 */
export interface CollectionAnalysisData {
  statDate?: string | null;
  receivedAmount?: number | null;
  overdueAmount?: number | null;
  overdueCustomerCount: number;
  agingDistribution: Record<string, number>;
  keyOverdueCustomers: Array<{
    customerId?: number | null;
    customerName?: string | null;
    overdueAmount?: number | null;
    days?: number | null;
  }>;
}

/** 行动项 */
export interface ActionItem {
  id: number;
  ruleCode?: string | null;
  refType?: string | null;
  refId?: number | null;
  customerId?: number | null;
  customerName?: string | null;
  title?: string | null;
  suggestion?: string | null;
  priority?: string | null;
  ownerId?: number | null;
  dueDate?: string | null;
  status?: string | null;
  closeReason?: string | null;
  createTime?: string | null;
}

/** 行动项筛选 */
export interface ActionItemQuery {
  ruleCode?: string;
  status?: string;
  priority?: string;
  refType?: string;
  customerId?: number | null;
  ownerId?: number | null;
  current?: number;
  size?: number;
}

/** 行动规则 */
export interface ActionRule {
  id: number;
  ruleCode: string;
  ruleName?: string | null;
  ruleType?: string | null;
  thresholdDays?: number | null;
  enabled?: boolean | null;
  description?: string | null;
}

/** 导出任务 */
export interface AnalyticsExport {
  id: number;
  exportNo: string;
  exportType?: string | null;
  status?: string | null;
  rowCount?: number | null;
  content?: string | null;
  error?: string | null;
  createTime?: string | null;
}

/** 看板 */
export function getDashboard(): Promise<DashboardData> {
  return http.get<DashboardData>('/crm/analytics/dashboard');
}

/** 漏斗分析 */
export function getFunnel(startDate?: string, endDate?: string): Promise<FunnelViewData> {
  return http.get<FunnelViewData>('/crm/analytics/funnel', { startDate, endDate });
}

/** 客户分析 */
export function getCustomerAnalysis(statDate?: string): Promise<CustomerAnalysisData> {
  return http.get<CustomerAnalysisData>('/crm/analytics/customer', { statDate });
}

/** 销售排行 */
export function getRanking(startDate?: string, endDate?: string): Promise<RankingRow[]> {
  return http.get<RankingRow[]>('/crm/analytics/ranking', { startDate, endDate });
}

/** 回款分析 */
export function getCollectionAnalysis(statDate?: string): Promise<CollectionAnalysisData> {
  return http.get<CollectionAnalysisData>('/crm/analytics/collection', { statDate });
}

/** 生成指标快照 */
export function generateSnapshot(statDate?: string): Promise<number> {
  return http.post<number>('/crm/analytics/snapshot', undefined, { statDate });
}

/** 指标快照分页 */
export function pageSnapshots(query: {
  metricCode?: string;
  startDate?: string;
  endDate?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<MetricSnapshot>> {
  return http.get<PageResult<MetricSnapshot>>('/crm/analytics/snapshot/page', query);
}

/** 行动项分页 */
export function pageActionItems(query: ActionItemQuery): Promise<PageResult<ActionItem>> {
  return http.get<PageResult<ActionItem>>('/crm/action-plan/page', query as Record<string, unknown>);
}

/** 生成行动项 */
export function generateActionItems(): Promise<number> {
  return http.post<number>('/crm/action-plan/generate');
}

/** 关闭行动项 */
export function closeActionItem(id: number, reason: string): Promise<ActionItem> {
  return http.post<ActionItem>(`/crm/action-plan/${id}/close`, { reason });
}

/** 查询行动规则 */
export function listActionRules(): Promise<ActionRule[]> {
  return http.get<ActionRule[]>('/crm/action-rule');
}

/** 创建导出任务 */
export function createExport(exportType: string, filtersJson?: string): Promise<AnalyticsExport> {
  return http.post<AnalyticsExport>('/crm/analytics/export', { exportType, filtersJson });
}

/** 读取导出内容 */
export function getExportContent(id: number): Promise<AnalyticsExport> {
  return http.get<AnalyticsExport>(`/crm/analytics/export/${id}/content`);
}

/** 代理下载导出文件（V13 起走 whale-file；历史导出回退 CSV 内容） */
export function downloadExport(id: number) {
  return download(`/crm/analytics/export/${id}/download`);
}
