// ERP 财务 API：凭证、应收应付、账龄与报表（经网关代理到 whale-erp）
import { http } from './client';
import type { PageResult } from './masterdata';

/** 凭证分录 */
export interface VoucherLine {
  voucherNo?: string;
  voucherDate?: string;
  lineNo?: number;
  accountCode: string;
  accountName?: string;
  summary?: string;
  debitAmount?: number;
  creditAmount?: number;
  currency?: string;
  exchangeRate?: number;
}

/** 会计凭证 */
export interface Voucher {
  id: number;
  voucherNo: string;
  voucherDate: string;
  periodCode: string;
  voucherType: string;
  status: string;
  currency: string;
  exchangeRate: number;
  totalDebit: number;
  totalCredit: number;
  sourceType?: string;
  sourceId?: string;
  reversalOfId?: number;
  reversedById?: number;
  remark?: string;
  postedAt?: string;
  postedBy?: number;
  lines?: VoucherLine[];
}

/** 凭证分录入参 */
export interface VoucherLineInput {
  accountCode: string;
  summary?: string;
  debitAmount?: number;
  creditAmount?: number;
}

/** 凭证录入入参 */
export interface VoucherInput {
  voucherDate: string;
  currency?: string;
  exchangeRate?: number;
  sourceType?: string;
  sourceId?: string;
  remark?: string;
  lines: VoucherLineInput[];
}

/** 应收单 */
export interface Receivable {
  id: number;
  docNo: string;
  customerId: number;
  customerName: string;
  docDate: string;
  dueDate: string;
  currency: string;
  amount: number;
  settledAmount: number;
  badDebtAmount: number;
  remainingAmount: number;
  status: string;
  remark?: string;
}

/** 应收单入参 */
export interface ReceivableInput {
  customerId: number;
  docNo?: string;
  docDate: string;
  dueDate: string;
  amount: number;
  currency?: string;
  remark?: string;
}

/** 应付单 */
export interface Payable {
  id: number;
  docNo: string;
  supplierId: number;
  supplierName: string;
  docDate: string;
  dueDate: string;
  currency: string;
  amount: number;
  settledAmount: number;
  remainingAmount: number;
  status: string;
  remark?: string;
}

/** 应付单入参 */
export interface PayableInput {
  supplierId: number;
  supplierName?: string;
  docNo?: string;
  docDate: string;
  dueDate: string;
  amount: number;
  currency?: string;
  remark?: string;
}

/** 核销/付款/坏账入参 */
export interface SettleInput {
  amount: number;
  settleDate?: string;
  settleType?: string;
  referenceNo?: string;
  remark?: string;
}

/** 账龄行 */
export interface AgingRow {
  dimension: string;
  partyId: number;
  partyName: string;
  total: number;
  notDue: number;
  overdue1To30: number;
  overdue31To60: number;
  overdue61To90: number;
  overdueOver90: number;
}

/** 科目余额行 */
export interface AccountBalance {
  accountCode: string;
  accountName: string;
  accountType: string;
  direction: string;
  debitTotal: number;
  creditTotal: number;
  balance: number;
}

/** 账簿明细行 */
export interface LedgerLine {
  voucherNo?: string;
  voucherDate?: string;
  lineNo?: number;
  accountCode: string;
  accountName?: string;
  summary?: string;
  debitAmount?: number;
  creditAmount?: number;
}

/** 明细账 */
export interface Ledger {
  accountCode: string;
  accountName: string;
  openingBalance: number;
  debitTotal: number;
  creditTotal: number;
  closingBalance: number;
  lines: LedgerLine[];
}

/** 财务报表行 */
export interface ReportRow {
  accountCode: string;
  accountName: string;
  accountType: string;
  amount: number;
}

/** 财务报表 */
export interface FinancialReport {
  reportType: string;
  periodCode: string;
  rows: ReportRow[];
  totalAssets?: number;
  totalLiabilities?: number;
  totalEquity?: number;
  totalRevenue?: number;
  totalExpenses?: number;
  netProfit: number;
}

/** 凭证查询参数 */
export interface VoucherQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  periodCode?: string;
}

/** 应收应付查询参数 */
export interface ArApQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  partyId?: number;
}

/** 构造查询串 */
function buildQuery(params: object): string {
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

/** 凭证分页 */
export function pageVouchers(params: VoucherQueryParams = {}): Promise<PageResult<Voucher>> {
  return http.get<PageResult<Voucher>>(`/erp/finance/voucher/page?${buildQuery(params)}`);
}

/** 凭证详情 */
export function getVoucher(id: number): Promise<Voucher> {
  return http.get<Voucher>(`/erp/finance/voucher/${id}`);
}

/** 录入凭证 */
export function createVoucher(input: VoucherInput): Promise<Voucher> {
  return http.post<Voucher>('/erp/finance/voucher', input);
}

/** 记账 */
export function postVoucher(id: number): Promise<Voucher> {
  return http.post<Voucher>(`/erp/finance/voucher/${id}/post`);
}

/** 红冲 */
export function reverseVoucher(id: number): Promise<Voucher> {
  return http.post<Voucher>(`/erp/finance/voucher/${id}/reverse`);
}

/** 应收分页 */
export function pageReceivables(params: ArApQueryParams = {}): Promise<PageResult<Receivable>> {
  return http.get<PageResult<Receivable>>(`/erp/finance/receivable/page?${buildQuery(params)}`);
}

/** 新增应收单 */
export function createReceivable(input: ReceivableInput): Promise<Receivable> {
  return http.post<Receivable>('/erp/finance/receivable', input);
}

/** 应收核销 */
export function settleReceivable(id: number, input: SettleInput): Promise<Receivable> {
  return http.post<Receivable>(`/erp/finance/receivable/${id}/settle`, input);
}

/** 应收坏账计提 */
export function provisionBadDebt(id: number, input: SettleInput): Promise<Receivable> {
  return http.post<Receivable>(`/erp/finance/receivable/${id}/bad-debt`, input);
}

/** 应付分页 */
export function pagePayables(params: ArApQueryParams = {}): Promise<PageResult<Payable>> {
  return http.get<PageResult<Payable>>(`/erp/finance/payable/page?${buildQuery(params)}`);
}

/** 新增应付单 */
export function createPayable(input: PayableInput): Promise<Payable> {
  return http.post<Payable>('/erp/finance/payable', input);
}

/** 应付付款核销 */
export function settlePayable(id: number, input: SettleInput): Promise<Payable> {
  return http.post<Payable>(`/erp/finance/payable/${id}/settle`, input);
}

/** 应收账龄 */
export function receivableAging(): Promise<AgingRow[]> {
  return http.get<AgingRow[]>('/erp/finance/aging/receivable');
}

/** 应付账龄 */
export function payableAging(): Promise<AgingRow[]> {
  return http.get<AgingRow[]>('/erp/finance/aging/payable');
}

/** 科目明细账 */
export function getLedger(
  accountCode: string,
  fromPeriod: string,
  toPeriod: string,
): Promise<Ledger> {
  return http.get<Ledger>(
    `/erp/finance/report/ledger?${buildQuery({ accountCode, fromPeriod, toPeriod })}`,
  );
}

/** 科目余额表 */
export function getAccountBalances(period: string): Promise<AccountBalance[]> {
  return http.get<AccountBalance[]>(`/erp/finance/report/account-balance?period=${period}`);
}

/** 资产负债表 */
export function getBalanceSheet(period: string): Promise<FinancialReport> {
  return http.get<FinancialReport>(`/erp/finance/report/balance-sheet?period=${period}`);
}

/** 利润表 */
export function getIncomeStatement(period: string): Promise<FinancialReport> {
  return http.get<FinancialReport>(`/erp/finance/report/income-statement?period=${period}`);
}
