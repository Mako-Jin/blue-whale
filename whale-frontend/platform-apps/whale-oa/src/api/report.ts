// OA 数据报表 API 层：报表目录、运行、快照与 CSV 导出
//
// 报表实时只读聚合；快照按月保存聚合结果；导出走 CSV 字节流并登记导出记录。

import { downloadBlob, http } from './client';
import type { PageResult } from './client';

/** 报表列定义 */
export interface OaReportColumn {
  key: string;
  label: string;
}

/** 报表目录项 */
export interface OaReportCatalogItem {
  code: string;
  title: string;
  description: string;
  columns: OaReportColumn[];
}

/** 报表运行结果 */
export interface OaReportResult {
  code: string;
  title: string;
  from?: string;
  to?: string;
  year?: number;
  columns: OaReportColumn[];
  rows: Record<string, unknown>[];
  rowCount: number;
  generatedAt: string;
}

/** 报表快照元信息 */
export interface OaReportSnapshot {
  id: number;
  reportCode: string;
  reportName: string;
  period: string;
  fromDate: string;
  toDate: string;
  rowCount: number;
  generatedBy?: number;
  generatedByName?: string;
  generatedAt: string;
}

/** 报表查询条件 */
export interface OaReportQuery {
  from?: string;
  to?: string;
  year?: number;
}

/** 拼装查询串 */
function toQueryString(query: OaReportQuery = {}): string {
  const params = new URLSearchParams();
  if (query.from) {
    params.set('from', query.from);
  }
  if (query.to) {
    params.set('to', query.to);
  }
  if (query.year !== undefined) {
    params.set('year', String(query.year));
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** 报表目录 */
export function getReportCatalog() {
  return http.get<OaReportCatalogItem[]>('/oa/report/catalog');
}

/** 运行报表 */
export function runReport(code: string, query: OaReportQuery = {}) {
  return http.get<OaReportResult>(`/oa/report/${code}${toQueryString(query)}`);
}

/** 生成月度快照 */
export function createReportSnapshot(code: string, period?: string) {
  const suffix = period ? `?period=${encodeURIComponent(period)}` : '';
  return http.post<OaReportSnapshot>(`/oa/report/${code}/snapshot${suffix}`);
}

/** 快照分页 */
export function pageReportSnapshots(
  params: { code?: string; current?: number; size?: number } = {},
) {
  const search = new URLSearchParams();
  if (params.code) {
    search.set('code', params.code);
  }
  search.set('current', String(params.current ?? 1));
  search.set('size', String(params.size ?? 10));
  return http.get<PageResult<OaReportSnapshot>>(`/oa/report/snapshots/page?${search.toString()}`);
}

/** 读取快照内容 */
export function getReportSnapshot(id: number) {
  return http.get<OaReportResult>(`/oa/report/snapshots/${id}`);
}

/** 下载报表 CSV */
export function downloadReportCsv(code: string, query: OaReportQuery = {}) {
  return downloadBlob(`/oa/report/${code}/export${toQueryString(query)}`);
}
