// ERP 业务 API 层：经网关 8080 代理到 whale-erp（/erp/**）
import { http } from './client';

/** 商品/物料状态 */
export type ErpItemStatus = 'ENABLED' | 'DISABLED';

/** 商品/物料记录 */
export interface ErpItem {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: ErpItemStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface ErpItemInput {
  id?: string;
  code: string;
  name: string;
  status: ErpItemStatus;
  remark?: string;
}

/** 分页信封（与后端 PageResult 对齐） */
interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 查询商品/物料列表 */
export async function listErpItems(): Promise<ErpItem[]> {
  const page = await http.get<PageResult<ErpItem>>('/erp/v1/item/page?current=1&size=200');
  return page.records;
}

/** 保存商品/物料（无 id 走新增，有 id 走编辑） */
export function saveErpItem(input: ErpItemInput): Promise<ErpItem> {
  const { id, ...body } = input;
  return id ? http.put<ErpItem>(`/erp/v1/item/${id}`, body) : http.post<ErpItem>('/erp/v1/item', body);
}

/** 供应商分类 */
export type ErpSupplierCategory = 'MATERIAL' | 'SERVICE' | 'OUTSOURCE' | 'LOGISTICS' | 'OTHER';

/** 供应商状态 */
export type ErpSupplierStatus = 'ENABLED' | 'DISABLED';

/** 供应商评级 */
export type ErpSupplierRating = 'A' | 'B' | 'C' | 'D';

/** 供应商主数据 */
export interface ErpSupplier {
  id: string;
  /** 供应商编码（租户内唯一） */
  code: string;
  /** 供应商名称 */
  name: string;
  /** 供应商简称 */
  shortName?: string;
  /** 供应商分类 */
  category: ErpSupplierCategory;
  /** 联系人 */
  contactName?: string;
  /** 联系电话 */
  contactPhone?: string;
  /** 联系邮箱 */
  contactEmail?: string;
  /** 纳税人识别号 */
  taxNo?: string;
  /** 开户银行 */
  bankName?: string;
  /** 银行账号 */
  bankAccount?: string;
  /** 地址 */
  address?: string;
  /** 状态 */
  status: ErpSupplierStatus;
  /** 供应商评级 */
  rating?: ErpSupplierRating;
  /** 备注 */
  remark?: string;
}

/** 供应商新增/编辑入参（编辑时携带 id） */
export interface ErpSupplierInput {
  id?: string;
  code: string;
  name: string;
  shortName?: string;
  category: ErpSupplierCategory;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  taxNo?: string;
  bankName?: string;
  bankAccount?: string;
  address?: string;
  status: ErpSupplierStatus;
  rating?: ErpSupplierRating;
  remark?: string;
}

/** 查询供应商列表 */
export async function listErpSuppliers(): Promise<ErpSupplier[]> {
  const page = await http.get<PageResult<ErpSupplier>>('/erp/v1/supplier/page?current=1&size=200');
  return page.records;
}

/** 保存供应商（无 id 走新增，有 id 走编辑） */
export function saveErpSupplier(input: ErpSupplierInput): Promise<ErpSupplier> {
  const { id, ...body } = input;
  return id
    ? http.put<ErpSupplier>(`/erp/v1/supplier/${id}`, body)
    : http.post<ErpSupplier>('/erp/v1/supplier', body);
}

/** 删除供应商（逻辑删除） */
export function deleteErpSupplier(id: string): Promise<void> {
  return http.del<void>(`/erp/v1/supplier/${id}`);
}
