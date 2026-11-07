// OA 报销 API 层：报销单、费用类别、费用标准
//
// 后端字段：金额为 number（保留 2 位）；日期为 YYYY-MM-DD；合计由服务端重算。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 报销状态 */
export type OaExpenseStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** ERP 应付推送状态 */
export type OaExpensePushStatus = 'NONE' | 'PENDING' | 'SENT' | 'FAILED';

/** 报销明细 */
export interface OaExpenseItem {
  id?: number;
  lineNo?: number;
  categoryCode: string;
  expenseDate: string;
  amount: number;
  taxAmount?: number;
  description?: string;
}

/** 报销发票 */
export interface OaExpenseInvoice {
  id?: number;
  invoiceCode?: string;
  invoiceNo: string;
  invoiceDate?: string;
  amount: number;
  taxAmount?: number;
  fileId?: number;
}

/** 报销单 */
export interface OaExpense {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  deptId?: number;
  categoryCode: string;
  title: string;
  totalAmount: number;
  currency?: string;
  status: OaExpenseStatus;
  approvalInstanceId?: number;
  erpPushStatus: OaExpensePushStatus;
  erpPayableNo?: string;
  submitTime?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  items?: OaExpenseItem[];
  invoices?: OaExpenseInvoice[];
  approvalVariables?: Record<string, unknown>;
}

/** 报销单入参 */
export interface OaExpenseInput {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  categoryCode: string;
  title: string;
  currency?: string;
  remark?: string;
  items: OaExpenseItem[];
  invoices?: OaExpenseInvoice[];
}

/** 报销查询 */
export interface OaExpenseQuery {
  current?: number;
  size?: number;
  code?: string;
  applicant?: string;
  applicantId?: number;
  categoryCode?: string;
  status?: OaExpenseStatus | '';
  erpPushStatus?: OaExpensePushStatus | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 费用类别 */
export interface OaExpenseCategory {
  id: number;
  code: string;
  name: string;
  accountSubject?: string;
  requireInvoice?: boolean;
  singleLimit?: number;
  sortNo?: number;
  enabled?: boolean;
}

/** 费用类别入参 */
export interface OaExpenseCategoryInput {
  id?: number;
  code: string;
  name: string;
  accountSubject?: string;
  requireInvoice?: boolean;
  singleLimit?: number;
  sortNo?: number;
  enabled?: boolean;
}

/** 费用标准 */
export interface OaExpenseStandard {
  id: number;
  categoryId: number;
  levelCode: string;
  limitAmount: number;
  enabled?: boolean;
  remark?: string;
}

/** 费用标准入参 */
export interface OaExpenseStandardInput {
  id?: number;
  categoryId: number;
  levelCode: string;
  limitAmount: number;
  enabled?: boolean;
  remark?: string;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询报销单 */
export function pageExpenses(params: OaExpenseQuery = {}): Promise<PageResult<OaExpense>> {
  return http.get<PageResult<OaExpense>>(`/oa/expense/page${buildQuery(params)}`);
}

/** 报销单详情（含明细与发票） */
export function getExpense(id: number): Promise<OaExpense> {
  return http.get<OaExpense>(`/oa/expense/${id}`);
}

/** 新建报销单 */
export function createExpense(input: OaExpenseInput): Promise<OaExpense> {
  return http.post<OaExpense>('/oa/expense', input);
}

/** 更新报销单 */
export function updateExpense(id: number, input: OaExpenseInput): Promise<OaExpense> {
  return http.put<OaExpense>(`/oa/expense/${id}`, input);
}

/** 删除报销单（仅待审批） */
export function deleteExpense(id: number): Promise<void> {
  return http.del<void>(`/oa/expense/${id}`);
}

/** 提交报销单（登记审批实例） */
export function submitExpense(id: number): Promise<OaExpense> {
  return http.post<OaExpense>(`/oa/expense/${id}/submit`);
}

/** 审批 / 撤销报销单 */
export function updateExpenseStatus(id: number, status: OaExpenseStatus): Promise<void> {
  return http.put<void>(`/oa/expense/${id}/status`, { status });
}

/** 手工重试推送 ERP 应付单 */
export function retryExpensePush(id: number): Promise<OaExpense> {
  return http.post<OaExpense>(`/oa/expense/${id}/push-retry`);
}

/** 查询全部费用类别 */
export function listCategories(): Promise<OaExpenseCategory[]> {
  return http.get<OaExpenseCategory[]>('/oa/expense/category');
}

/** 新增 / 更新费用类别 */
export function saveCategory(input: OaExpenseCategoryInput): Promise<OaExpenseCategory> {
  return http.post<OaExpenseCategory>('/oa/expense/category', input);
}

/** 删除费用类别 */
export function deleteCategory(id: number): Promise<void> {
  return http.del<void>(`/oa/expense/category/${id}`);
}

/** 查询指定类别的费用标准 */
export function listStandards(categoryId: number): Promise<OaExpenseStandard[]> {
  return http.get<OaExpenseStandard[]>(`/oa/expense/category/${categoryId}/standards`);
}

/** 新增 / 更新费用标准 */
export function saveStandard(input: OaExpenseStandardInput): Promise<OaExpenseStandard> {
  return http.post<OaExpenseStandard>('/oa/expense/category/standards', input);
}

/** 删除费用标准 */
export function deleteStandard(id: number): Promise<void> {
  return http.del<void>(`/oa/expense/category/standards/${id}`);
}
