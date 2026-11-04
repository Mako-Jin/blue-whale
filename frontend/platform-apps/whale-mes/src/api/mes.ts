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

/** 工序任务状态 */
export type MesTaskStatus =
  | 'PLANNED'
  | 'DISPATCHED'
  | 'IN_PROGRESS'
  | 'PAUSED'
  | 'COMPLETED'
  | 'SCRAPPED';

/** 工序任务 */
export interface MesTask {
  id: string;
  /** 任务编号 */
  taskNo: string;
  /** 工单 ID */
  workOrderId: string;
  /** 工单编码 */
  workOrderCode: string;
  /** ERP 生产订单号 */
  erpOrderNo?: string;
  /** 工序顺序 */
  seqNo: number;
  /** 工序编码 */
  operationCode: string;
  /** 工序名称 */
  operationName: string;
  /** 工作中心 ID */
  workCenterId?: string;
  /** 工作中心编码 */
  workCenterCode?: string;
  /** 计划数量 */
  plannedQuantity?: number | string;
  /** 已派工数量 */
  dispatchedQuantity?: number | string;
  /** 累计报工数量 */
  reportedQuantity?: number | string;
  /** 累计合格数量 */
  qualifiedQuantity?: number | string;
  /** 累计不良数量 */
  defectQuantity?: number | string;
  /** 累计完工数量 */
  completedQuantity?: number | string;
  /** 累计报废数量 */
  scrappedQuantity?: number | string;
  /** 状态 */
  status: MesTaskStatus;
  /** 优先级 */
  priority?: MesWorkOrderPriority;
  /** 计划开工时间 */
  plannedStart?: string;
  /** 计划完工时间 */
  plannedEnd?: string;
  /** 备注 */
  remark?: string;
}

/** 任务查询条件 */
export interface MesTaskQuery {
  taskNo?: string;
  workOrderId?: string;
  workOrderCode?: string;
  operationCode?: string;
  status?: MesTaskStatus;
  priority?: MesWorkOrderPriority;
  current?: number;
  size?: number;
}

/** 派工入参 */
export interface MesDispatchInput {
  /** 任务 ID 列表 */
  taskIds: string[];
  /** 派工产线 ID */
  lineId: string;
  /** 派工班组 ID */
  teamId?: string;
  /** 备注 */
  remark?: string;
}

/** 生产看板 */
export interface MesBoard {
  boardDate: string;
  targetQuantity: number | string;
  completedQuantity: number | string;
  defectQuantity: number | string;
  achievementRate: number | string;
  exceptionCount: number;
  activeTaskCount: number;
  activeTasks: MesTask[];
}

/** 分页查询工序任务 */
export function pageMesTasks(query: MesTaskQuery = {}): Promise<PageResult<MesTask>> {
  const queryString = toQueryString({ current: 1, size: 200, ...query });
  return http.get<PageResult<MesTask>>(`/mes/dispatch/task/page?${queryString}`);
}

/** 拆分工单为工序任务（不传明细时按工艺路线自动拆分） */
export function splitMesWorkOrder(id: string): Promise<MesTask[]> {
  return http.post<MesTask[]>(`/mes/dispatch/work-order/${id}/split`, {});
}

/** 批量派工 */
export function dispatchMesTasks(input: MesDispatchInput): Promise<string> {
  return http.post<string>('/mes/dispatch/dispatch', input);
}

/** 撤回派工单 */
export function withdrawMesDispatch(dispatchOrderId: string): Promise<void> {
  return http.post<void>(`/mes/dispatch/dispatch/${dispatchOrderId}/withdraw`, {});
}

/** 查询生产看板 */
export function fetchMesBoard(params: { lineId?: string; teamId?: string } = {}): Promise<MesBoard> {
  const queryString = toQueryString(params);
  return http.get<MesBoard>(`/mes/dispatch/board${queryString ? `?${queryString}` : ''}`);
}

/** 任务执行动作：start / pause / resume / complete / scrap */
export type MesTaskAction = 'start' | 'pause' | 'resume' | 'complete' | 'scrap';

/** 执行任务动作 */
export function runMesTaskAction(
  id: string,
  action: MesTaskAction,
  body: { quantity?: number; remark?: string } = {},
): Promise<MesTask> {
  return http.post<MesTask>(`/mes/execution/task/${id}/${action}`, body);
}

/** 不良明细入参 */
export interface MesReportDefectInput {
  defectCode: string;
  defectName?: string;
  quantity: number;
}

/** 报工入参 */
export interface MesReportInput {
  taskId: string;
  reportQuantity: number;
  qualifiedQuantity: number;
  defectQuantity: number;
  reporter?: string;
  remark?: string;
  defects?: MesReportDefectInput[];
}

/** 报工记录 */
export interface MesProductionReport {
  id: string;
  reportNo: string;
  taskId: string;
  taskNo: string;
  workOrderId: string;
  workOrderCode: string;
  operationCode: string;
  stationCode?: string;
  reportQuantity: number | string;
  qualifiedQuantity: number | string;
  defectQuantity: number | string;
  status: string;
  reporter?: string;
  reportedAt: string;
  remark?: string;
}

/** 扫码报工 */
export function reportMesProduction(input: MesReportInput): Promise<MesProductionReport> {
  return http.post<MesProductionReport>('/mes/execution/report', input);
}

/** 分页查询报工记录 */
export function pageMesReports(
  query: { taskId?: string; workOrderId?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesProductionReport>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesProductionReport>>(`/mes/execution/report/page?${queryString}`);
}

/** 在制品快照 */
export interface MesWip {
  taskId: string;
  taskNo: string;
  workOrderId: string;
  operationCode: string;
  dispatchedQuantity?: number | string;
  completedQuantity?: number | string;
  scrappedQuantity?: number | string;
  wipQuantity?: number | string;
  snapshotTime?: string;
}

/** 查询在制品 */
export function fetchMesWip(workOrderId?: string): Promise<MesWip[]> {
  const queryString = toQueryString({ workOrderId });
  return http.get<MesWip[]>(`/mes/execution/wip${queryString ? `?${queryString}` : ''}`);
}
