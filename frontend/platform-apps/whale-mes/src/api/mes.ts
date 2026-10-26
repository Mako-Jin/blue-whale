// MES 业务 API 层：经网关 8080 代理到 whale-mes（/mes/**）
import { http } from './client';

/** 工单状态 */
export type MesWorkOrderStatus = 'ENABLED' | 'DISABLED';

/** 生产工单记录 */
export interface MesWorkOrder {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: MesWorkOrderStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface MesWorkOrderInput {
  id?: string;
  code: string;
  name: string;
  status: MesWorkOrderStatus;
  remark?: string;
}

/** 分页信封（与后端 PageResult 对齐） */
interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 查询生产工单列表 */
export async function listMesWorkOrders(): Promise<MesWorkOrder[]> {
  const page = await http.get<PageResult<MesWorkOrder>>('/mes/work-order/page?current=1&size=200');
  return page.records;
}

/** 保存生产工单（无 id 走新增，有 id 走编辑） */
export function saveMesWorkOrder(input: MesWorkOrderInput): Promise<MesWorkOrder> {
  const { id, ...body } = input;
  return id ? http.put<MesWorkOrder>(`/mes/work-order/${id}`, body) : http.post<MesWorkOrder>('/mes/work-order', body);
}
