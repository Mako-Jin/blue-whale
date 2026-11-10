// PDM 业务 API 层：经网关 8080 代理到 whale-pdm（/pdm/**）
import { http } from './client';

/** 产品状态 */
export type PdmProductStatus = 'ENABLED' | 'DISABLED';

/** 产品/图纸记录 */
export interface PdmProduct {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: PdmProductStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface PdmProductInput {
  id?: string;
  code: string;
  name: string;
  status: PdmProductStatus;
  remark?: string;
}

/** 分页信封（与后端 PageResult 对齐） */
interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 查询产品/图纸列表 */
export async function listPdmProducts(): Promise<PdmProduct[]> {
  const page = await http.get<PageResult<PdmProduct>>('/pdm/product/page?current=1&size=200');
  return page.records;
}

/** 保存产品/图纸（无 id 走新增，有 id 走编辑） */
export function savePdmProduct(input: PdmProductInput): Promise<PdmProduct> {
  const { id, ...body } = input;
  return id
    ? http.put<PdmProduct>(`/pdm/product/${id}`, body)
    : http.post<PdmProduct>('/pdm/product', body);
}
