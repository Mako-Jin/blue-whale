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
  const page = await http.get<PageResult<ErpItem>>('/erp/item/page?current=1&size=200');
  return page.records;
}

/** 保存商品/物料（无 id 走新增，有 id 走编辑） */
export function saveErpItem(input: ErpItemInput): Promise<ErpItem> {
  const { id, ...body } = input;
  return id ? http.put<ErpItem>(`/erp/item/${id}`, body) : http.post<ErpItem>('/erp/item', body);
}
