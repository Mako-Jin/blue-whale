// CRM 客户工具 API（M1-4 合并 / M1-9 CSV 导入导出）
//
// 合并：先预览引用数、冲突警告，再确认执行；引用重写覆盖商机/线索/报价/合同/订单/回款/开票/工单/退货。
// 导入：预检不落库；执行仅创建 OK 行，硬冲突与重复导入自动跳过。
// 导出：返回 CSV 文本，默认脱敏手机/邮箱/税号。

import { http } from './client';

/** 合并命令 */
export interface CustomerMergeInput {
  sourceId: number;
  targetId: number;
  confirmPrimaryContactConflict?: boolean;
  operatorNote?: string;
}

/** 合并预览 */
export interface CustomerMergePreview {
  sourceId: number;
  sourceCode?: string | null;
  sourceName?: string | null;
  sourceStatus?: string | null;
  targetId: number;
  targetCode?: string | null;
  targetName?: string | null;
  targetStatus?: string | null;
  references: Record<string, number>;
  warnings: string[];
}

/** 合并结果 */
export interface CustomerMergeResult {
  sourceId?: number | null;
  targetId?: number | null;
  rewrittenReferences?: Record<string, number>;
  movedContacts?: number | null;
  movedFollowUps?: number | null;
  movedAddresses?: number | null;
  merged?: boolean | null;
}

/** 导入行预检结果 */
export interface CustomerImportRow {
  rowNo: number;
  name?: string | null;
  status: string;
  reason?: string | null;
}

/** 导入预检结果 */
export interface CustomerImportPreview {
  total: number;
  valid: number;
  invalid: number;
  duplicate: number;
  rows: CustomerImportRow[];
}

/** 导入执行结果 */
export interface CustomerImportResult {
  created: number;
  skipped: number;
  failed: number;
  rows: CustomerImportRow[];
}

/** 合并预览 */
export function previewMerge(input: CustomerMergeInput): Promise<CustomerMergePreview> {
  return http.post<CustomerMergePreview>('/crm/customer/merge/preview', input);
}

/** 执行合并 */
export function mergeCustomer(input: CustomerMergeInput): Promise<CustomerMergeResult> {
  return http.post<CustomerMergeResult>('/crm/customer/merge', input);
}

/** 导入预检 */
export function previewImport(content: string): Promise<CustomerImportPreview> {
  return http.post<CustomerImportPreview>('/crm/customer/import/preview', { content });
}

/** 执行导入 */
export function executeImport(content: string): Promise<CustomerImportResult> {
  return http.post<CustomerImportResult>('/crm/customer/import/execute', { content });
}

/** 导出 CSV */
export function exportCustomers(query?: Record<string, unknown>): Promise<string> {
  return http.get<string>('/crm/customer/export', query);
}
