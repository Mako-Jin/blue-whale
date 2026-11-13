// ERP 主数据 API：科目、会计期间、客户、仓库与参考数据（经网关代理到 whale-erp）
import { http } from './client';

/** 通用分页信封（与后端 PageResult 对齐） */
export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 分页查询参数 */
export interface PageParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
}

/** 会计科目 */
export interface ErpAccount {
  id: number;
  code: string;
  name: string;
  accountType: string;
  direction: string;
  parentCode?: string;
  leaf: boolean;
  status: string;
  remark?: string;
}

/** 会计科目入参 */
export interface ErpAccountInput {
  id?: number;
  code: string;
  name: string;
  accountType: string;
  direction: string;
  parentCode?: string;
  leaf: boolean;
  status: string;
  remark?: string;
}

/** 会计期间 */
export interface ErpPeriod {
  id: number;
  periodCode: string;
  startDate: string;
  endDate: string;
  status: string;
  closedAt?: string;
  closedBy?: number;
  remark?: string;
}

/** 客户主数据 */
export interface ErpCustomer {
  id: number;
  code: string;
  name: string;
  shortName?: string;
  taxNo?: string;
  contactName?: string;
  contactPhone?: string;
  creditLimit: number;
  status: string;
  remark?: string;
}

/** 客户主数据入参 */
export interface ErpCustomerInput {
  id?: number;
  code: string;
  name: string;
  shortName?: string;
  taxNo?: string;
  contactName?: string;
  contactPhone?: string;
  creditLimit?: number;
  status: string;
  remark?: string;
}

/** 仓库 */
export interface ErpWarehouse {
  id: number;
  code: string;
  name: string;
  manager?: string;
  address?: string;
  status: string;
  remark?: string;
}

/** 仓库入参 */
export interface ErpWarehouseInput {
  id?: number;
  code: string;
  name: string;
  manager?: string;
  address?: string;
  status: string;
  remark?: string;
}

/** 编码-名称选项 */
export interface CodeName {
  id: number;
  code: string;
  name: string;
  extra?: string;
}

/** 参考数据 */
export interface ReferenceData {
  uoms: CodeName[];
  currencies: CodeName[];
  taxCodes: CodeName[];
}

/** 构造查询串 */
function buildQuery(params: PageParams): string {
  const search = new URLSearchParams();
  search.set('current', String(params.current ?? 1));
  search.set('size', String(params.size ?? 20));
  if (params.keyword) search.set('keyword', params.keyword);
  if (params.status) search.set('status', params.status);
  return search.toString();
}

/** 科目分页 */
export function pageAccounts(params: PageParams = {}): Promise<PageResult<ErpAccount>> {
  return http.get<PageResult<ErpAccount>>(`/erp/master-data/account/page?${buildQuery(params)}`);
}

/** 保存科目（无 id 新增，有 id 编辑） */
export function saveAccount(input: ErpAccountInput): Promise<ErpAccount> {
  const { id, ...body } = input;
  return id
    ? http.put<ErpAccount>(`/erp/master-data/account/${id}`, body)
    : http.post<ErpAccount>('/erp/master-data/account', body);
}

/** 会计期间分页 */
export function pagePeriods(params: PageParams = {}): Promise<PageResult<ErpPeriod>> {
  return http.get<PageResult<ErpPeriod>>(`/erp/master-data/period/page?${buildQuery(params)}`);
}

/** 关账 */
export function closePeriod(id: number): Promise<ErpPeriod> {
  return http.post<ErpPeriod>(`/erp/master-data/period/${id}/close`);
}

/** 反结账 */
export function reopenPeriod(id: number): Promise<ErpPeriod> {
  return http.post<ErpPeriod>(`/erp/master-data/period/${id}/reopen`);
}

/** 客户分页 */
export function pageCustomers(params: PageParams = {}): Promise<PageResult<ErpCustomer>> {
  return http.get<PageResult<ErpCustomer>>(`/erp/master-data/customer/page?${buildQuery(params)}`);
}

/** 保存客户 */
export function saveCustomer(input: ErpCustomerInput): Promise<ErpCustomer> {
  const { id, ...body } = input;
  return id
    ? http.put<ErpCustomer>(`/erp/master-data/customer/${id}`, body)
    : http.post<ErpCustomer>('/erp/master-data/customer', body);
}

/** 仓库分页 */
export function pageWarehouses(params: PageParams = {}): Promise<PageResult<ErpWarehouse>> {
  return http.get<PageResult<ErpWarehouse>>(
    `/erp/master-data/warehouse/page?${buildQuery(params)}`,
  );
}

/** 保存仓库 */
export function saveWarehouse(input: ErpWarehouseInput): Promise<ErpWarehouse> {
  const { id, ...body } = input;
  return id
    ? http.put<ErpWarehouse>(`/erp/master-data/warehouse/${id}`, body)
    : http.post<ErpWarehouse>('/erp/master-data/warehouse', body);
}

/** 参考数据 */
export function getReferenceData(): Promise<ReferenceData> {
  return http.get<ReferenceData>('/erp/master-data/reference');
}
