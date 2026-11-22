// ERP 数据分析与报表中心 API：驾驶舱、多维分析、报表定义/计划、预警规则/事件
import { http } from './client';
import type { PageResult } from './masterdata';

/** 分析查询参数 */
export interface BiQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  periodCode?: string;
  dimensionType?: string;
  metricType?: string;
  ruleType?: string;
  fromDate?: string;
  toDate?: string;
}

/** 驾驶舱指标 */
export interface CockpitView {
  snapshotNo: string;
  periodCode: string;
  statDate: string;
  revenueAmount: number;
  costAmount: number;
  profitAmount: number;
  orderCount: number;
  alertCount: number;
  status: string;
  refreshedAt: string;
}

/** 驾驶舱刷新入参 */
export interface CockpitRefreshInput {
  periodCode: string;
  statDate?: string;
}

/** 多维分析明细行 */
export interface CubeRow {
  dimensionKey: string;
  dimensionName: string;
  metricValue: number;
  orderCount: number;
}

/** 多维分析结果 */
export interface CubeResult {
  dimensionType: string;
  metricType: string;
  fromDate: string;
  toDate: string;
  rows: CubeRow[];
  totalMetric: number;
  totalOrderCount: number;
  note?: string;
}

/** 报表定义 */
export interface ReportDefinitionView {
  reportNo: string;
  reportName: string;
  reportType: string;
  dimensionType: string;
  metricType: string;
  filterJson?: string;
  status: string;
  owner?: string;
  remark?: string;
}

/** 报表定义入参 */
export interface ReportDefinitionInput {
  reportNo: string;
  reportName: string;
  reportType?: string;
  dimensionType: string;
  metricType: string;
  filterJson?: string;
  status?: string;
  owner?: string;
  remark?: string;
}

/** 报表计划 */
export interface ReportScheduleView {
  scheduleNo: string;
  reportNo: string;
  scheduleName: string;
  cronExpression: string;
  channel: string;
  recipients?: string;
  lastRunTime?: string;
  nextRunTime?: string;
  status: string;
  remark?: string;
}

/** 报表计划入参 */
export interface ReportScheduleInput {
  scheduleNo: string;
  reportNo: string;
  scheduleName: string;
  cronExpression: string;
  channel?: string;
  recipients?: string;
  status?: string;
  remark?: string;
}

/** 预警规则 */
export interface AlertRuleView {
  ruleNo: string;
  ruleName: string;
  ruleType: string;
  thresholdValue: number;
  periodType: string;
  level: string;
  suggestion?: string;
  status: string;
  remark?: string;
}

/** 预警规则入参 */
export interface AlertRuleInput {
  ruleNo: string;
  ruleName: string;
  ruleType: string;
  thresholdValue?: number;
  periodType?: string;
  level?: string;
  suggestion?: string;
  status?: string;
  remark?: string;
}

/** 预警事件 */
export interface AlertEventView {
  id: number;
  eventNo: string;
  ruleNo: string;
  ruleType: string;
  objectKey: string;
  periodCode: string;
  metricValue: number;
  thresholdValue: number;
  level: string;
  status: string;
  suggestion?: string;
  triggeredAt: string;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  closedBy?: string;
  closedAt?: string;
  remark?: string;
}

/** 预警评估结果 */
export interface AlertEvaluationView {
  evaluatedRules: number;
  createdEvents: number;
  duplicateSkipped: number;
  skippedRules: string[];
  note?: string;
}

/** 预警事件处理入参 */
export interface AlertHandleInput {
  operator?: string;
  remark?: string;
}

/** 维度中文标签 */
export const DIMENSION_LABEL: Record<string, string> = {
  CUSTOMER: '客户',
  ITEM: '产品/物料',
  WAREHOUSE: '仓库',
};

/** 指标中文标签 */
export const METRIC_LABEL: Record<string, string> = {
  REVENUE: '收入',
  QUANTITY: '发货数量',
  ORDER_COUNT: '订单数',
  COST: '出库成本',
};

/** 规则类型中文标签 */
export const RULE_TYPE_LABEL: Record<string, string> = {
  STOCK_LOW: '库存不足',
  RECEIVABLE_OVERDUE: '超期应收',
  QUALITY_OPEN_NCR: '质量异常',
  BUDGET_EXCEEDED: '超预算',
};

/** 预警状态中文标签 */
export const ALERT_STATUS_LABEL: Record<string, string> = {
  OPEN: '待处理',
  ACKNOWLEDGED: '已确认',
  CLOSED: '已关闭',
};

/** 级别中文标签 */
export const ALERT_LEVEL_LABEL: Record<string, string> = {
  INFO: '提示',
  WARN: '警告',
  CRITICAL: '严重',
};

/** 构造查询串（空值不下发，默认每页 20 条） */
function buildQuery(params: BiQueryParams): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  });
  if (!search.has('current')) search.set('current', '1');
  if (!search.has('size')) search.set('size', '20');
  return search.toString();
}

/** 查询经营驾驶舱（缓存缺失时后端按当前口径懒加载） */
export function getCockpit(periodCode?: string): Promise<CockpitView> {
  const suffix = periodCode ? `?periodCode=${encodeURIComponent(periodCode)}` : '';
  return http.get<CockpitView>(`/erp/v1/bi/cockpit${suffix}`);
}

/** 刷新驾驶舱快照 */
export function refreshCockpit(input: CockpitRefreshInput): Promise<CockpitView> {
  return http.post<CockpitView>('/erp/v1/bi/cockpit/refresh', input);
}

/** 主动失效快照 */
export function invalidateCockpit(periodCode: string): Promise<void> {
  return http.post<void>(
    `/erp/v1/bi/cockpit/invalidate?periodCode=${encodeURIComponent(periodCode)}`,
  );
}

/** 多维分析 */
export function queryCube(params: BiQueryParams): Promise<CubeResult> {
  return http.get<CubeResult>(`/erp/v1/bi/cube?${buildQuery(params)}`);
}

/** 报表定义分页 */
export function pageReportDefinitions(
  params: BiQueryParams = {},
): Promise<PageResult<ReportDefinitionView>> {
  return http.get<PageResult<ReportDefinitionView>>(
    `/erp/v1/bi/report/definition/page?${buildQuery(params)}`,
  );
}

/** 新增报表定义 */
export function createReportDefinition(
  input: ReportDefinitionInput,
): Promise<ReportDefinitionView> {
  return http.post<ReportDefinitionView>('/erp/v1/bi/report/definition', input);
}

/** 编辑报表定义 */
export function updateReportDefinition(
  reportNo: string,
  input: ReportDefinitionInput,
): Promise<ReportDefinitionView> {
  return http.put<ReportDefinitionView>(
    `/erp/v1/bi/report/definition/${encodeURIComponent(reportNo)}`,
    input,
  );
}

/** 执行报表 */
export function executeReport(reportNo: string, from?: string, to?: string): Promise<CubeResult> {
  const search = new URLSearchParams();
  if (from) search.set('from', from);
  if (to) search.set('to', to);
  const query = search.toString();
  return http.post<CubeResult>(
    `/erp/v1/bi/report/${encodeURIComponent(reportNo)}/execute${query ? `?${query}` : ''}`,
  );
}

/** 导出报表 CSV */
export function exportReportCsv(reportNo: string, from?: string, to?: string): Promise<string> {
  const search = new URLSearchParams();
  if (from) search.set('from', from);
  if (to) search.set('to', to);
  const query = search.toString();
  return http.get<string>(
    `/erp/v1/bi/report/${encodeURIComponent(reportNo)}/export${query ? `?${query}` : ''}`,
  );
}

/** 报表计划分页 */
export function pageReportSchedules(
  params: BiQueryParams = {},
): Promise<PageResult<ReportScheduleView>> {
  return http.get<PageResult<ReportScheduleView>>(
    `/erp/v1/bi/report/schedule/page?${buildQuery(params)}`,
  );
}

/** 新增报表计划 */
export function createReportSchedule(input: ReportScheduleInput): Promise<ReportScheduleView> {
  return http.post<ReportScheduleView>('/erp/v1/bi/report/schedule', input);
}

/** 编辑报表计划 */
export function updateReportSchedule(
  scheduleNo: string,
  input: ReportScheduleInput,
): Promise<ReportScheduleView> {
  return http.put<ReportScheduleView>(
    `/erp/v1/bi/report/schedule/${encodeURIComponent(scheduleNo)}`,
    input,
  );
}

/** 手动执行报表计划 */
export function runReportSchedule(scheduleNo: string): Promise<ReportScheduleView> {
  return http.post<ReportScheduleView>(
    `/erp/v1/bi/report/schedule/${encodeURIComponent(scheduleNo)}/run`,
  );
}

/** 预警规则分页 */
export function pageAlertRules(params: BiQueryParams = {}): Promise<PageResult<AlertRuleView>> {
  return http.get<PageResult<AlertRuleView>>(`/erp/v1/bi/alert/rule/page?${buildQuery(params)}`);
}

/** 新增预警规则 */
export function createAlertRule(input: AlertRuleInput): Promise<AlertRuleView> {
  return http.post<AlertRuleView>('/erp/v1/bi/alert/rule', input);
}

/** 编辑预警规则 */
export function updateAlertRule(ruleNo: string, input: AlertRuleInput): Promise<AlertRuleView> {
  return http.put<AlertRuleView>(`/erp/v1/bi/alert/rule/${encodeURIComponent(ruleNo)}`, input);
}

/** 预警事件分页 */
export function pageAlertEvents(params: BiQueryParams = {}): Promise<PageResult<AlertEventView>> {
  return http.get<PageResult<AlertEventView>>(`/erp/v1/bi/alert/event/page?${buildQuery(params)}`);
}

/** 评估预警规则 */
export function evaluateAlerts(ruleNo?: string): Promise<AlertEvaluationView> {
  const suffix = ruleNo ? `?ruleNo=${encodeURIComponent(ruleNo)}` : '';
  return http.post<AlertEvaluationView>(`/erp/v1/bi/alert/evaluate${suffix}`);
}

/** 确认预警事件 */
export function acknowledgeAlert(
  id: number,
  input: AlertHandleInput = {},
): Promise<AlertEventView> {
  return http.post<AlertEventView>(`/erp/v1/bi/alert/event/${id}/acknowledge`, input);
}

/** 关闭预警事件 */
export function closeAlert(id: number, input: AlertHandleInput = {}): Promise<AlertEventView> {
  return http.post<AlertEventView>(`/erp/v1/bi/alert/event/${id}/close`, input);
}
