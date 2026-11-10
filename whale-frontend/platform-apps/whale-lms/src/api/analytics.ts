// LMS 看板接口封装（M6：学习覆盖率 / 完成率 / 通过率 / 学分分布 / 导出）。
//
// 口径提示：
//   - 覆盖率 = 学习人数 / 在职人数；完成率 = 完成人数 / 学习人数（分母是「已开始学的人」）；
//   - 通过率 = 通过人数 / 参考人数，两者都按人去重（补考不重复计数）；
//   - 看板接口只读，缺当天快照时实时算但不落库；刷新是单独的 POST，避免访问频率污染趋势。

import { getToken, http } from './client';

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 学分分布档位 */
export interface CreditBucketItem {
  label: string;
  earners: number;
}

/** 指标快照（行） */
export interface MetricSnapshotItem {
  statDate: string;
  scopeType: string;
  scopeName: string;
  memberTotal: number;
  learnerTotal: number;
  coverageRate: number;
  learnerCompleted: number;
  completionRate: number;
  examTakerTotal: number;
  examPassedTotal: number;
  passRate: number;
  creditTotal: number;
  creditEarners: number;
  computedAt?: string;
}

/** 看板总览 */
export interface AnalyticsOverview {
  statDate: string;
  scopeType: string;
  scopeName: string;
  memberTotal: number;
  learnerTotal: number;
  coverageRate: number;
  learnerCompleted: number;
  completionRate: number;
  examTakerTotal: number;
  examPassedTotal: number;
  passRate: number;
  creditTotal: number;
  creditEarners: number;
  creditDistribution: CreditBucketItem[];
  /** true 表示当天尚无快照，当前展示的是实时计算结果（未落库） */
  live: boolean;
  trend: MetricSnapshotItem[];
}

/** 快照刷新结果 */
export interface RefreshResult {
  statDate: string;
  created: boolean;
  snapshot: MetricSnapshotItem;
}

/** 看板总览（statDate 为空取今天） */
export function getOverview(statDate?: string) {
  return http.get<AnalyticsOverview>('/lms/analytics/overview', { statDate });
}

/** 刷新并落库某统计日快照（同日重跑覆盖） */
export function refreshSnapshot(statDate?: string) {
  return http.post<RefreshResult>(`/lms/analytics/refresh${statDate ? `?statDate=${statDate}` : ''}`);
}

/** 历史快照分页 */
export function pageSnapshots(params: {
  from?: string;
  to?: string;
  scopeType?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<MetricSnapshotItem>>('/lms/analytics/snapshot/page', { ...params });
}

/**
 * 导出近 N 天快照为 CSV。
 *
 * <p>不能用 http 客户端：导出返回的是附件流而不是 Result 信封，
 * 交给浏览器原生下载才能保留 Content-Disposition 的文件名。</p>
 */
export async function exportCsv(days: number): Promise<void> {
  const token = getToken();
  const response = await fetch(`/lms/analytics/export?days=${days}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    throw new Error(`导出失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `lms-analytics-${days}d.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}
