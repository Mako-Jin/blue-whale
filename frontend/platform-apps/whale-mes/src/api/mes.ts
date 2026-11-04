// MES 业务 API 层：经网关 8080 代理到 whale-mes（/mes/**）
import { http } from './client';

/** 工单生命周期状态 */
export type MesWorkOrderStatus =
  | 'RECEIVED'
  | 'DISPATCHED'
  | 'IN_PROGRESS'
  | 'PAUSED'
  | 'COMPLETED'
  | 'CLOSED'
  | 'CANCELLED';

/** 工单优先级 */
export type MesWorkOrderPriority = 'NORMAL' | 'URGENT';

/** 生产工单记录 */
export interface MesWorkOrder {
  id: string;
  /** 工单编码 */
  code: string;
  /** 工单名称 */
  name: string;
  /** 生命周期状态 */
  status: MesWorkOrderStatus;
  /** ERP 生产订单号 */
  erpOrderNo?: string;
  /** 物料编码 */
  itemCode?: string;
  /** 物料名称 */
  itemName?: string;
  /** 计划数量 */
  plannedQuantity?: number | string;
  /** 计量单位 */
  uom?: string;
  /** 工艺路线版本快照 */
  routingVersion?: string;
  /** 优先级 */
  priority?: MesWorkOrderPriority;
  /** 计划开工时间 */
  plannedStart?: string;
  /** 计划完工时间 */
  plannedEnd?: string;
  /** 乐观锁版本号 */
  version?: number;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface MesWorkOrderInput {
  id?: string;
  code: string;
  name: string;
  status?: MesWorkOrderStatus;
  erpOrderNo?: string;
  itemCode?: string;
  itemName?: string;
  plannedQuantity?: number;
  uom?: string;
  routingVersion?: string;
  priority?: MesWorkOrderPriority;
  plannedStart?: string;
  plannedEnd?: string;
  remark?: string;
}

/** 工单查询条件 */
export interface MesWorkOrderQuery {
  code?: string;
  name?: string;
  erpOrderNo?: string;
  itemCode?: string;
  status?: MesWorkOrderStatus;
  priority?: MesWorkOrderPriority;
  current?: number;
  size?: number;
}

/** 分页信封（与后端 PageResult 对齐） */
export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 将查询对象转为 URL 查询串（忽略空值） */
function toQueryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  return search.toString();
}

/** 分页查询生产工单 */
export function pageMesWorkOrders(query: MesWorkOrderQuery = {}): Promise<PageResult<MesWorkOrder>> {
  const queryString = toQueryString({ current: 1, size: 200, ...query });
  return http.get<PageResult<MesWorkOrder>>(`/mes/work-order/page?${queryString}`);
}

/** 查询生产工单列表（取首屏全量） */
export async function listMesWorkOrders(query: MesWorkOrderQuery = {}): Promise<MesWorkOrder[]> {
  const page = await pageMesWorkOrders(query);
  return page.records;
}

/** 保存生产工单（无 id 走新增，有 id 走编辑） */
export function saveMesWorkOrder(input: MesWorkOrderInput): Promise<MesWorkOrder> {
  const { id, ...body } = input;
  return id
    ? http.put<MesWorkOrder>(`/mes/work-order/${id}`, body)
    : http.post<MesWorkOrder>('/mes/work-order', body);
}

/** 删除生产工单（仅已接收 / 已取消允许删除） */
export function deleteMesWorkOrder(id: string): Promise<void> {
  return http.del<void>(`/mes/work-order/${id}`);
}

/** MES 车间主数据类型 */
export type MasterDataKind =
  | 'workshop'
  | 'line'
  | 'work-center'
  | 'station'
  | 'team'
  | 'shift'
  | 'operation'
  | 'defect-code'
  | 'reason-code';

/** 车间主数据记录（各类型共用，未使用字段缺省） */
export interface MesMasterData {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: 'ENABLED' | 'DISABLED';
  /** 备注 */
  remark?: string;
  /** 所属车间 ID */
  workshopId?: string;
  /** 所属产线 ID */
  lineId?: string;
  /** 所属工作中心 ID */
  workCenterId?: string;
  /** 工作中心类型 */
  centerType?: 'MACHINE' | 'LINE' | 'CELL';
  /** 每班产能 */
  capacityPerShift?: number | string;
  /** 车间位置 */
  address?: string;
  /** 车间负责人 */
  managerName?: string;
  /** 班组长 */
  leaderName?: string;
  /** 班次开始时间 */
  startTime?: string;
  /** 班次结束时间 */
  endTime?: string;
  /** 是否跨日：0 否 / 1 是 */
  crossDay?: number;
  /** 工序类型 */
  operationType?: 'PROCESS' | 'INSPECT' | 'PACK';
  /** 标准工时（分钟） */
  standardMinutes?: number | string;
  /** 分类 */
  category?: string;
  /** 是否影响 OEE 可用率：0 否 / 1 是 */
  affectAvailability?: number;
}

/** 车间主数据新增/编辑入参 */
export type MesMasterDataInput = Omit<MesMasterData, 'id'> & { id?: string };

/** 车间主数据查询条件 */
export interface MesMasterDataQuery {
  code?: string;
  name?: string;
  status?: 'ENABLED' | 'DISABLED';
  workshopId?: string;
  lineId?: string;
  workCenterId?: string;
  current?: number;
  size?: number;
}

/** 分页查询车间主数据 */
export function pageMesMasterData(
  kind: MasterDataKind,
  query: MesMasterDataQuery = {},
): Promise<PageResult<MesMasterData>> {
  const queryString = toQueryString({ current: 1, size: 200, ...query });
  return http.get<PageResult<MesMasterData>>(`/mes/master-data/${kind}/page?${queryString}`);
}

/** 查询启用状态的主数据选项 */
export function listMesMasterDataOptions(kind: MasterDataKind): Promise<MesMasterData[]> {
  return http.get<MesMasterData[]>(`/mes/master-data/${kind}/options`);
}

/** 保存车间主数据（无 id 走新增，有 id 走编辑） */
export function saveMesMasterData(
  kind: MasterDataKind,
  input: MesMasterDataInput,
): Promise<MesMasterData> {
  const { id, ...body } = input;
  return id
    ? http.put<MesMasterData>(`/mes/master-data/${kind}/${id}`, body)
    : http.post<MesMasterData>(`/mes/master-data/${kind}`, body);
}

/** 删除车间主数据 */
export function deleteMesMasterData(kind: MasterDataKind, id: string): Promise<void> {
  return http.del<void>(`/mes/master-data/${kind}/${id}`);
}
