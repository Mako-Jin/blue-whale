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

/** 条码规则 */
export interface MesBarcodeRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  prefix: string;
  codeType: 'CODE128' | 'QR';
  serialLength: number;
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
}

/** 条码规则保存入参 */
export type MesBarcodeRuleInput = Omit<MesBarcodeRule, 'id'> & { id?: string };

/** 物料条码 */
export interface MesBarcode {
  id: string;
  barcodeValue: string;
  codeType?: 'CODE128' | 'QR';
  batchId: string;
  batchNo: string;
  itemCode: string;
  objectType: string;
  status: 'ACTIVE' | 'VOID';
  printPayload?: string;
  lastScanTime?: string;
  scanCount?: number;
  createTime?: string;
}

/** 条码查询条件 */
export interface MesBarcodeQuery {
  barcodeValue?: string;
  batchNo?: string;
  itemCode?: string;
  status?: 'ACTIVE' | 'VOID';
  current?: number;
  size?: number;
}

/** 物料批次 */
export interface MesBatch {
  id: string;
  batchNo: string;
  itemCode: string;
  itemName?: string;
  batchType: 'RAW' | 'SEMI' | 'FINISHED';
  sourceType: 'PURCHASE' | 'PRODUCTION' | 'RETURN';
  workOrderId?: string;
  workOrderCode?: string;
  supplierCode?: string;
  quantity?: number | string;
  consumedQuantity?: number | string;
  remainingQuantity?: number | string;
  uom?: string;
  productionDate?: string;
  expiryDate?: string;
  status: 'ACTIVE' | 'CLOSED';
  remark?: string;
  createTime?: string;
}

/** 批次建档入参 */
export interface MesBatchInput {
  batchNo?: string;
  itemCode: string;
  itemName?: string;
  batchType?: 'RAW' | 'SEMI' | 'FINISHED';
  sourceType?: 'PURCHASE' | 'PRODUCTION' | 'RETURN';
  workOrderId?: string;
  workOrderCode?: string;
  supplierCode?: string;
  quantity: number;
  uom?: string;
  productionDate?: string;
  expiryDate?: string;
  remark?: string;
}

/** 批次查询条件 */
export interface MesBatchQuery {
  batchNo?: string;
  itemCode?: string;
  batchType?: 'RAW' | 'SEMI' | 'FINISHED';
  status?: 'ACTIVE' | 'CLOSED';
  current?: number;
  size?: number;
}

/** 追溯节点 */
export interface MesTraceNode {
  level: number;
  batchId: string;
  batchNo: string;
  itemCode: string;
  itemName?: string;
  batchType?: string;
  status?: string;
  quantity?: number | string;
  uom?: string;
  relation: 'PARENT' | 'CHILD';
}

/** 扫码规则 */
export interface MesScanRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  stationCode?: string;
  operationCode?: string;
  expectedItemCode?: string;
  action: 'BLOCK' | 'WARN';
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
}

/** 扫码规则保存入参 */
export type MesScanRuleInput = Omit<MesScanRule, 'id'> & { id?: string };

/** 扫码校验结果 */
export interface MesScanResult {
  passed: boolean;
  blocked: boolean;
  duplicate: boolean;
  message: string;
  barcodeValue: string;
  batchNo?: string;
  itemCode?: string;
  ruleCode?: string;
}

/** 线边库存 */
export interface MesLineSideStock {
  id: string;
  lineId: string;
  lineCode: string;
  warehouseCode: string;
  itemCode: string;
  itemName?: string;
  batchNo: string;
  uom?: string;
  quantity: number | string;
  safetyQuantity: number | string;
  belowSafety: boolean;
  updateTime?: string;
}

/** 线边流水 */
export interface MesLineSideMovement {
  id: string;
  movementNo: string;
  lineId: string;
  lineCode: string;
  warehouseCode: string;
  itemCode: string;
  batchNo: string;
  movementType: string;
  direction: number;
  quantity: number | string;
  beforeQuantity: number | string;
  afterQuantity: number | string;
  operator?: string;
  occurredAt?: string;
}

/** 线边出入库入参 */
export interface MesLineSideOperationInput {
  lineId: string;
  lineCode: string;
  warehouseCode: string;
  itemCode: string;
  itemName?: string;
  batchNo?: string;
  quantity: number;
  uom?: string;
  movementType?: 'INBOUND' | 'RETURN';
  workOrderId?: string;
  taskId?: string;
  operator?: string;
  remark?: string;
}

/** 叫料补货请求 */
export interface MesReplenishment {
  id: string;
  requestNo: string;
  lineId: string;
  lineCode: string;
  warehouseCode: string;
  itemCode: string;
  itemName?: string;
  quantity: number | string;
  uom?: string;
  status: 'REQUESTED' | 'CONFIRMED' | 'CANCELLED';
  reason?: string;
  requestedBy?: string;
  requestedAt?: string;
  erpTransferNo?: string;
  remark?: string;
}

/** 叫料补货入参 */
export interface MesReplenishmentInput {
  requestNo?: string;
  lineId: string;
  lineCode: string;
  warehouseCode: string;
  itemCode: string;
  itemName?: string;
  quantity: number;
  uom?: string;
  reason?: string;
  requestedBy?: string;
  remark?: string;
}

/** 分页查询条码规则 */
export function pageMesBarcodeRules(
  query: { ruleCode?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesBarcodeRule>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesBarcodeRule>>(`/mes/trace/barcode/rule/page?${queryString}`);
}

/** 保存条码规则 */
export function saveMesBarcodeRule(input: MesBarcodeRuleInput): Promise<MesBarcodeRule> {
  return http.post<MesBarcodeRule>('/mes/trace/barcode/rule', input);
}

/** 分页查询条码 */
export function pageMesBarcodes(query: MesBarcodeQuery = {}): Promise<PageResult<MesBarcode>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesBarcode>>(`/mes/trace/barcode/page?${queryString}`);
}

/** 批量生成条码 */
export function generateMesBarcodes(input: {
  ruleId: string;
  batchId: string;
  quantity: number;
  remark?: string;
}): Promise<MesBarcode[]> {
  return http.post<MesBarcode[]>('/mes/trace/barcode/generate', input);
}

/** 作废条码 */
export function voidMesBarcode(id: string): Promise<void> {
  return http.post<void>(`/mes/trace/barcode/${id}/void`, {});
}

/** 分页查询批次 */
export function pageMesBatches(query: MesBatchQuery = {}): Promise<PageResult<MesBatch>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesBatch>>(`/mes/trace/batch/page?${queryString}`);
}

/** 批次建档 */
export function createMesBatch(input: MesBatchInput): Promise<MesBatch> {
  return http.post<MesBatch>('/mes/trace/batch', input);
}

/** 封批 */
export function closeMesBatch(id: string): Promise<MesBatch> {
  return http.post<MesBatch>(`/mes/trace/batch/${id}/close`, {});
}

/** 建立批次谱系链接 */
export function linkMesBatches(input: {
  parentBatchNo: string;
  childBatchNo: string;
  quantity: number;
  uom?: string;
  workOrderId?: string;
  remark?: string;
}): Promise<string> {
  return http.post<string>('/mes/trace/trace/link', input);
}

/** 追溯方向 */
export type MesTraceDirection = 'forward' | 'backward';

/** 正反向追溯 */
export function fetchMesTrace(
  direction: MesTraceDirection,
  batchNo: string,
  depth = 3,
): Promise<MesTraceNode[]> {
  const queryString = toQueryString({ batchNo, depth });
  return http.get<MesTraceNode[]>(`/mes/trace/trace/${direction}?${queryString}`);
}

/** 扫码校验 */
export function validateMesScan(input: {
  barcodeValue: string;
  stationCode?: string;
  operationCode?: string;
  scanNo?: string;
  taskId?: string;
}): Promise<MesScanResult> {
  return http.post<MesScanResult>('/mes/trace/scan/validate', input);
}

/** 分页查询扫码规则 */
export function pageMesScanRules(
  query: { ruleCode?: string; stationCode?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesScanRule>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesScanRule>>(`/mes/trace/scan/rule/page?${queryString}`);
}

/** 保存扫码规则 */
export function saveMesScanRule(input: MesScanRuleInput): Promise<MesScanRule> {
  return http.post<MesScanRule>('/mes/trace/scan/rule', input);
}

/** 分页查询线边库存 */
export function pageMesLineSideStock(
  query: { lineId?: string; warehouseCode?: string; itemCode?: string; batchNo?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesLineSideStock>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesLineSideStock>>(`/mes/trace/line-side/stock/page?${queryString}`);
}

/** 线边入库 / 退料退回 */
export function inboundMesLineSide(input: MesLineSideOperationInput): Promise<string> {
  return http.post<string>('/mes/trace/line-side/stock/inbound', input);
}

/** 线边领用 */
export function issueMesLineSide(input: MesLineSideOperationInput): Promise<string> {
  return http.post<string>('/mes/trace/line-side/stock/issue', input);
}

/** 分页查询线边流水 */
export function pageMesLineSideMovements(
  query: { lineId?: string; itemCode?: string; batchNo?: string; movementType?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesLineSideMovement>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesLineSideMovement>>(`/mes/trace/line-side/movement/page?${queryString}`);
}

/** 叫料补货请求 */
export function requestMesReplenishment(input: MesReplenishmentInput): Promise<MesReplenishment> {
  return http.post<MesReplenishment>('/mes/trace/line-side/replenish', input);
}

/** 分页查询补货请求 */
export function pageMesReplenishments(
  query: { lineId?: string; itemCode?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesReplenishment>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesReplenishment>>(`/mes/trace/line-side/replenish/page?${queryString}`);
}

/** 检验类型 */
export type MesInspectionType = 'FIRST_ARTICLE' | 'IPQC';

/** 检验任务状态 */
export type MesInspectionTaskStatus = 'PENDING' | 'INSPECTED' | 'PASSED' | 'FAILED' | 'CANCELLED';

/** 检验计划项 */
export interface MesInspectionPlanItem {
  id?: string;
  planId?: string;
  itemNo: number;
  itemName: string;
  inspectionMethod?: 'VISUAL' | 'MEASURE' | 'FUNCTION' | 'TEST';
  standardText?: string;
  minValue?: number | string;
  maxValue?: number | string;
  unit?: string;
  criticalFlag?: number;
  sortNo?: number;
}

/** 检验计划 */
export interface MesInspectionPlan {
  id: string;
  planNo: string;
  planName: string;
  inspectionType: MesInspectionType;
  itemCode?: string;
  operationCode?: string;
  lineId?: string;
  lineCode?: string;
  sampleSize: number;
  frequencyMinutes: number;
  autoNcr?: number;
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
  createTime?: string;
  items?: MesInspectionPlanItem[];
}

/** 检验计划保存入参 */
export interface MesInspectionPlanInput {
  id?: string;
  planNo?: string;
  planName: string;
  inspectionType: MesInspectionType;
  itemCode?: string;
  operationCode?: string;
  lineId?: string;
  lineCode?: string;
  sampleSize: number;
  frequencyMinutes: number;
  autoNcr?: number;
  status?: 'ENABLED' | 'DISABLED';
  remark?: string;
  items: MesInspectionPlanItem[];
}

/** 检验任务 */
export interface MesInspectionTask {
  id: string;
  taskNo: string;
  taskType: MesInspectionType;
  planId?: string;
  planNo?: string;
  workOrderId?: string;
  workOrderCode?: string;
  execTaskId?: string;
  execTaskNo?: string;
  operationCode?: string;
  lineId?: string;
  lineCode?: string;
  stationCode?: string;
  batchNo?: string;
  sampleSize: number;
  inspectedQuantity: number;
  status: MesInspectionTaskStatus;
  inspector?: string;
  judge?: string;
  judgedAt?: string;
  conclusion?: string;
  ncrId?: string;
  remark?: string;
  createTime?: string;
}

/** 检验结果 */
export interface MesInspectionResult {
  id: string;
  planItemId?: string;
  itemNo: number;
  itemName?: string;
  sampleNo: number;
  measuredValue?: string;
  numericValue?: number | string;
  result: 'OK' | 'NG';
  inspector?: string;
  remark?: string;
}

/** 检验不良 */
export interface MesInspectionDefect {
  id: string;
  defectCode: string;
  defectName?: string;
  quantity: number | string;
  severity?: string;
  description?: string;
}

/** 检验任务详情 */
export interface MesInspectionTaskDetail {
  task: MesInspectionTask;
  results: MesInspectionResult[];
  defects: MesInspectionDefect[];
  planItemCount: number;
  completedItemCount: number;
  judgeReady: boolean;
}

/** 检验任务创建入参 */
export interface MesInspectionTaskInput {
  taskType: MesInspectionType;
  planId: string;
  workOrderId?: string;
  workOrderCode?: string;
  execTaskId?: string;
  execTaskNo?: string;
  operationCode?: string;
  lineId?: string;
  lineCode?: string;
  stationCode?: string;
  batchNo?: string;
  sampleSize?: number;
  inspector?: string;
  remark?: string;
}

/** 检验结果录入入参 */
export interface MesInspectionResultInput {
  planItemId: string;
  itemNo: number;
  itemName?: string;
  sampleNo?: number;
  measuredValue?: string;
  numericValue?: number;
  result: 'OK' | 'NG';
  remark?: string;
}

/** 检验结果提交入参 */
export interface MesInspectionSubmitInput {
  inspector?: string;
  remark?: string;
  results: MesInspectionResultInput[];
}

/** 首件门禁结果 */
export interface MesFirstArticleGate {
  workOrderId?: string;
  operationCode?: string;
  passed: boolean;
  taskNo?: string;
  status?: string;
  judgedAt?: string;
  message: string;
}

/** 不合格品记录 NCR */
export interface MesNcr {
  id: string;
  ncrNo: string;
  sourceType: string;
  inspectionTaskId?: string;
  inspectionTaskNo?: string;
  workOrderId?: string;
  workOrderCode?: string;
  execTaskId?: string;
  execTaskNo?: string;
  itemCode?: string;
  itemName?: string;
  batchNo?: string;
  operationCode?: string;
  defectCode?: string;
  defectName?: string;
  defectQuantity: number | string;
  status: 'OPEN' | 'DISPOSITION' | 'REWORK' | 'REPAIR' | 'SCRAP' | 'CONCESSION' | 'CLOSED';
  conclusion?: string;
  closedBy?: string;
  closedAt?: string;
  remark?: string;
  createTime?: string;
}

/** NCR 手工创建入参 */
export interface MesNcrInput {
  sourceType?: 'INSPECTION' | 'REPORT' | 'MANUAL';
  workOrderId?: string;
  workOrderCode?: string;
  execTaskId?: string;
  execTaskNo?: string;
  itemCode?: string;
  itemName?: string;
  batchNo?: string;
  operationCode?: string;
  defectCode?: string;
  defectName?: string;
  defectQuantity: number;
  remark?: string;
}

/** 不合格品处置 */
export interface MesDisposition {
  id: string;
  dispositionNo: string;
  ncrId: string;
  ncrNo: string;
  dispositionType: 'REWORK' | 'REPAIR' | 'SCRAP' | 'CONCESSION';
  quantity: number | string;
  reason?: string;
  approvedBy?: string;
  status: 'CONFIRMED' | 'EXECUTED' | 'CANCELLED';
  executedAt?: string;
  remark?: string;
  createTime?: string;
}

/** 处置创建入参 */
export interface MesDispositionInput {
  ncrId: string;
  dispositionType: 'REWORK' | 'REPAIR' | 'SCRAP' | 'CONCESSION';
  quantity?: number;
  reason?: string;
  approvedBy?: string;
  remark?: string;
}

/** 返工单 */
export interface MesReworkOrder {
  id: string;
  reworkNo: string;
  ncrId: string;
  ncrNo: string;
  dispositionId: string;
  workOrderId?: string;
  workOrderCode?: string;
  execTaskId?: string;
  execTaskNo?: string;
  itemCode?: string;
  itemName?: string;
  batchNo?: string;
  quantity: number | string;
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  reason?: string;
  operator?: string;
  startedAt?: string;
  completedAt?: string;
  remark?: string;
  createTime?: string;
}

/** SPC 规则 */
export interface MesSpcRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  planItemId: string;
  operationCode?: string;
  chartType: string;
  subgroupSize: number;
  centerLine?: number | string;
  ucl?: number | string;
  lcl?: number | string;
  rUcl?: number | string;
  rLcl?: number | string;
  alertAction: 'WARN' | 'BLOCK';
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
  createTime?: string;
}

/** SPC 规则保存入参 */
export interface MesSpcRuleInput {
  id?: string;
  ruleCode?: string;
  ruleName: string;
  planItemId: string;
  operationCode?: string;
  chartType?: 'XBAR_R';
  subgroupSize?: number;
  centerLine?: number;
  ucl?: number;
  lcl?: number;
  rUcl?: number;
  rLcl?: number;
  alertAction?: 'WARN' | 'BLOCK';
  status?: 'ENABLED' | 'DISABLED';
  remark?: string;
}

/** SPC 样本 */
export interface MesSpcSample {
  id: string;
  sampleNo: string;
  ruleId: string;
  ruleCode?: string;
  planItemId: string;
  inspectionTaskId?: string;
  operationCode?: string;
  subgroupNo: number;
  sampleSeq: number;
  subgroupSize: number;
  measuredValue: number | string;
  subgroupXbar?: number | string;
  subgroupRange?: number | string;
  alertFlag: number;
  alertRule?: string;
  sampledAt?: string;
}

/** SPC 样本录入入参 */
export interface MesSpcSampleInput {
  ruleId: string;
  planItemId: string;
  inspectionTaskId?: string;
  operationCode?: string;
  subgroupNo: number;
  sampleSeq?: number;
  measuredValue: number;
}

/** SPC 控制图表点 */
export interface MesSpcChartPoint {
  subgroupNo: number;
  sampleCount: number;
  subgroupXbar?: number | string;
  subgroupRange?: number | string;
  alertFlag: number;
  alertRule?: string;
  sampledAt?: string;
}

/** 质量看板 */
export interface MesQualityBoard {
  boardDate: string;
  inspectionTaskCount: number;
  pendingTaskCount: number;
  failedTaskCount: number;
  firstArticlePassRate: number | string;
  ncrOpenCount: number;
  ncrClosedCount: number;
  dispositionCount: number;
  reworkOpenCount: number;
  spcAlertCount: number;
}

/** 分页查询检验计划 */
export function pageMesInspectionPlans(
  query: { planNo?: string; planName?: string; inspectionType?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesInspectionPlan>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesInspectionPlan>>(`/mes/quality/inspection-plan/page?${queryString}`);
}

/** 查询检验计划详情（含检验项） */
export function getMesInspectionPlan(id: string): Promise<MesInspectionPlan> {
  return http.get<MesInspectionPlan>(`/mes/quality/inspection-plan/${id}`);
}

/** 保存检验计划（含检验项） */
export function saveMesInspectionPlan(input: MesInspectionPlanInput): Promise<MesInspectionPlan> {
  return http.post<MesInspectionPlan>('/mes/quality/inspection-plan', input);
}

/** 创建首件 / IPQC 检验任务 */
export function createMesInspectionTask(input: MesInspectionTaskInput): Promise<MesInspectionTask> {
  return http.post<MesInspectionTask>('/mes/quality/inspection/task', input);
}

/** 分页查询检验任务 */
export function pageMesInspectionTasks(
  query: { taskNo?: string; taskType?: string; workOrderId?: string; workOrderCode?: string; operationCode?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesInspectionTask>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesInspectionTask>>(`/mes/quality/inspection/task/page?${queryString}`);
}

/** 查询检验任务详情 */
export function getMesInspectionTask(id: string): Promise<MesInspectionTaskDetail> {
  return http.get<MesInspectionTaskDetail>(`/mes/quality/inspection/task/${id}`);
}

/** 录入检验结果 */
export function submitMesInspectionResults(
  id: string,
  input: MesInspectionSubmitInput,
): Promise<MesInspectionTaskDetail> {
  return http.post<MesInspectionTaskDetail>(`/mes/quality/inspection/task/${id}/results`, input);
}

/** 判定检验任务 */
export function judgeMesInspectionTask(
  id: string,
  input: { judge?: string; conclusion: string },
): Promise<MesInspectionTask> {
  return http.post<MesInspectionTask>(`/mes/quality/inspection/task/${id}/judge`, input);
}

/** 首件门禁查询 */
export function fetchMesFirstArticleGate(params: { workOrderId?: string; operationCode?: string }): Promise<MesFirstArticleGate> {
  const queryString = toQueryString(params);
  return http.get<MesFirstArticleGate>(`/mes/quality/inspection/first-article/gate${queryString ? `?${queryString}` : ''}`);
}

/** 分页查询 NCR */
export function pageMesNcrs(
  query: { ncrNo?: string; workOrderId?: string; batchNo?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesNcr>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesNcr>>(`/mes/quality/ncr/page?${queryString}`);
}

/** 手工创建 NCR */
export function createMesNcr(input: MesNcrInput): Promise<MesNcr> {
  return http.post<MesNcr>('/mes/quality/ncr', input);
}

/** 关闭 NCR */
export function closeMesNcr(id: string, input: { conclusion: string; closedBy?: string }): Promise<MesNcr> {
  return http.post<MesNcr>(`/mes/quality/ncr/${id}/close`, input);
}

/** 分页查询处置 */
export function pageMesDispositions(
  query: { dispositionNo?: string; ncrNo?: string; dispositionType?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesDisposition>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesDisposition>>(`/mes/quality/disposition/page?${queryString}`);
}

/** 创建处置 */
export function createMesDisposition(input: MesDispositionInput): Promise<MesDisposition> {
  return http.post<MesDisposition>('/mes/quality/disposition', input);
}

/** 执行处置 */
export function executeMesDisposition(id: string): Promise<MesDisposition> {
  return http.post<MesDisposition>(`/mes/quality/disposition/${id}/execute`, {});
}

/** 分页查询返工单 */
export function pageMesReworks(
  query: { reworkNo?: string; ncrNo?: string; workOrderId?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesReworkOrder>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesReworkOrder>>(`/mes/quality/rework/page?${queryString}`);
}

/** 返工开工 */
export function startMesRework(id: string, input: { operator?: string; remark?: string } = {}): Promise<MesReworkOrder> {
  return http.post<MesReworkOrder>(`/mes/quality/rework/${id}/start`, input);
}

/** 返工完工 */
export function completeMesRework(id: string, input: { operator?: string; remark?: string } = {}): Promise<MesReworkOrder> {
  return http.post<MesReworkOrder>(`/mes/quality/rework/${id}/complete`, input);
}

/** 分页查询 SPC 规则 */
export function pageMesSpcRules(
  query: { ruleCode?: string; planItemId?: string; status?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesSpcRule>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesSpcRule>>(`/mes/quality/spc/rule/page?${queryString}`);
}

/** 保存 SPC 规则 */
export function saveMesSpcRule(input: MesSpcRuleInput): Promise<MesSpcRule> {
  return http.post<MesSpcRule>('/mes/quality/spc/rule', input);
}

/** 录入 SPC 样本 */
export function submitMesSpcSample(input: MesSpcSampleInput): Promise<MesSpcSample> {
  return http.post<MesSpcSample>('/mes/quality/spc/sample', input);
}

/** 分页查询 SPC 样本 */
export function pageMesSpcSamples(
  query: { ruleId?: string; planItemId?: string; alertFlag?: number; current?: number; size?: number } = {},
): Promise<PageResult<MesSpcSample>> {
  const queryString = toQueryString({ current: 1, size: 100, ...query });
  return http.get<PageResult<MesSpcSample>>(`/mes/quality/spc/sample/page?${queryString}`);
}

/** 查询 SPC 控制图表点 */
export function fetchMesSpcChart(
  params: { planItemId: string; ruleId?: string; limit?: number },
): Promise<MesSpcChartPoint[]> {
  const queryString = toQueryString(params);
  return http.get<MesSpcChartPoint[]>(`/mes/quality/spc/chart?${queryString}`);
}

/** 查询质量看板 */
export function fetchMesQualityBoard(params: { boardDate?: string; lineId?: string } = {}): Promise<MesQualityBoard> {
  const queryString = toQueryString(params);
  return http.get<MesQualityBoard>(`/mes/quality/board${queryString ? `?${queryString}` : ''}`);
}

/** 设备状态 */
export type MesEquipmentStatus = 'RUNNING' | 'IDLE' | 'FAULT' | 'MAINTENANCE' | 'OFFLINE';

/** 设备台账 */
export interface MesEquipment {
  id: string;
  /** 设备编码 */
  equipmentCode: string;
  /** 设备名称 */
  equipmentName: string;
  /** 设备类型 */
  equipmentType?: string;
  /** 所属车间 */
  workshopId?: string;
  workshopCode?: string;
  /** 所属产线 */
  lineId?: string;
  lineCode?: string;
  /** 所属工作中心 */
  workCenterId?: string;
  workCenterCode?: string;
  /** 工位编码 */
  stationCode?: string;
  /** 型号 */
  modelNo?: string;
  /** 制造商 */
  manufacturer?: string;
  /** 采购日期 */
  purchaseDate?: string;
  /** 保养周期（天） */
  maintenanceCycleDays?: number;
  /** 状态基线 */
  statusBaseline?: string;
  /** 当前状态 */
  status: MesEquipmentStatus;
  /** 是否启用：0 否 / 1 是 */
  enabled: number;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 设备台账新增/编辑入参 */
export type MesEquipmentInput = Omit<MesEquipment, 'id' | 'status' | 'enabled' | 'createTime'> & {
  id?: string;
  status?: MesEquipmentStatus;
  enabled?: number;
};

/** 设备台账查询条件 */
export interface MesEquipmentQuery {
  equipmentCode?: string;
  equipmentName?: string;
  equipmentType?: string;
  lineId?: string;
  status?: MesEquipmentStatus;
  enabled?: number;
  current?: number;
  size?: number;
}

/** 设备状态事件 */
export interface MesEquipmentStatusEvent {
  id: string;
  equipmentId: string;
  equipmentCode: string;
  fromStatus?: string;
  toStatus: string;
  eventType?: string;
  occurredAt?: string;
  durationSeconds?: number;
  operator?: string;
  remark?: string;
}

/** 设备状态变更入参 */
export interface MesEquipmentStatusChangeInput {
  toStatus: MesEquipmentStatus;
  eventType?: string;
  occurredAt?: string;
  operator?: string;
  reasonCode?: string;
  reasonName?: string;
  sourceEventId?: string;
  remark?: string;
}

/** 停机记录 */
export interface MesEquipmentDowntime {
  downtimeNo: string;
  equipmentId: string;
  equipmentCode: string;
  reasonCode?: string;
  reasonName?: string;
  /** 是否计划停机：0 否 / 1 是 */
  plannedFlag: number;
  startAt?: string;
  endAt?: string;
  durationMinutes?: number | string;
  remark?: string;
}

/** 设备开工门禁结果 */
export interface MesEquipmentGate {
  equipmentCode: string;
  status?: string;
  passed: boolean;
  message?: string;
}

/** 保养计划 */
export interface MesMaintenancePlan {
  id: string;
  planNo: string;
  equipmentId: string;
  equipmentCode: string;
  maintenanceType?: string;
  cycleDays?: number;
  /** 保养项，分号分隔 */
  checklist?: string;
  lastDoneAt?: string;
  nextDueAt?: string;
  status: string;
  remark?: string;
  createTime?: string;
}

/** 保养计划新增/编辑入参 */
export type MesMaintenancePlanInput = Omit<MesMaintenancePlan, 'id' | 'planNo' | 'equipmentCode' | 'lastDoneAt' | 'nextDueAt' | 'createTime'> & {
  id?: string;
  planNo?: string;
};

/** 保养/维修查询条件 */
export interface MesMaintenanceQuery {
  docNo?: string;
  equipmentId?: string;
  equipmentCode?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 保养工单 */
export interface MesMaintenanceOrder {
  id: string;
  orderNo: string;
  planId?: string;
  planNo?: string;
  equipmentId: string;
  equipmentCode: string;
  maintenanceType?: string;
  checklist?: string;
  dueAt?: string;
  status: string;
  assignee?: string;
  startedAt?: string;
  completedAt?: string;
  completedBy?: string;
  acceptedBy?: string;
  acceptedAt?: string;
  conclusion?: string;
  checklistResult?: string;
  remark?: string;
  createTime?: string;
}

/** 保养工单创建入参 */
export interface MesMaintenanceOrderInput {
  planId?: string;
  dueAt?: string;
  assignee?: string;
  remark?: string;
}

/** 维修工单 */
export interface MesRepairOrder {
  id: string;
  repairNo: string;
  equipmentId: string;
  equipmentCode: string;
  faultDesc?: string;
  severity?: string;
  status: string;
  reportedBy?: string;
  reportedAt?: string;
  assignee?: string;
  assignedAt?: string;
  startedAt?: string;
  repairDesc?: string;
  verifiedBy?: string;
  verifiedAt?: string;
  closedAt?: string;
  verifyConclusion?: string;
  downtimeMinutes?: number | string;
  remark?: string;
  createTime?: string;
}

/** 维修报修入参 */
export interface MesRepairInput {
  equipmentId: string;
  faultDesc: string;
  severity?: string;
  reportedBy?: string;
  reportedAt?: string;
  remark?: string;
}

/** 维修动作入参 */
export interface MesRepairActionInput {
  assignee?: string;
  repairDesc?: string;
  verifiedBy?: string;
  verifyConclusion?: string;
  operator?: string;
  remark?: string;
}

/** 维修查询条件 */
export interface MesRepairQuery {
  repairNo?: string;
  equipmentId?: string;
  equipmentCode?: string;
  status?: string;
  severity?: string;
  current?: number;
  size?: number;
}

/** 备件主档 */
export interface MesSparePart {
  id: string;
  partCode: string;
  partName: string;
  spec?: string;
  uom?: string;
  safetyStock?: number | string;
  status: string;
  remark?: string;
  createTime?: string;
}

/** 备件主档入参 */
export type MesSparePartInput = Omit<MesSparePart, 'id' | 'createTime'> & { id?: string };

/** 备件查询条件 */
export interface MesSparePartQuery {
  partCode?: string;
  partName?: string;
  warehouseCode?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 备件库存 */
export interface MesSparePartStock {
  id: string;
  partId: string;
  partCode: string;
  warehouseCode: string;
  quantity: number | string;
  updateTime?: string;
}

/** 备件流水 */
export interface MesSparePartMovement {
  movementNo: string;
  partCode: string;
  warehouseCode: string;
  movementType: string;
  quantity: number | string;
  beforeQuantity?: number | string;
  afterQuantity?: number | string;
  refNo?: string;
  operator?: string;
  remark?: string;
  createTime?: string;
}

/** 备件出入库入参 */
export interface MesSparePartStockInput {
  partCode: string;
  warehouseCode?: string;
  quantity: number;
  refNo?: string;
  operator?: string;
  remark?: string;
}

/** OEE 快照 */
export interface MesOeeSnapshot {
  id: string;
  snapshotNo: string;
  equipmentId: string;
  equipmentCode: string;
  shiftDate: string;
  shiftCode: string;
  plannedMinutes?: number | string;
  plannedDowntimeMinutes?: number | string;
  runMinutes?: number | string;
  downtimeMinutes?: number | string;
  totalCount?: number | string;
  goodCount?: number | string;
  idealCycleSeconds?: number | string;
  availability?: number | string;
  performance?: number | string;
  quality?: number | string;
  oee?: number | string;
  formulaConfig?: string;
  computedAt?: string;
}

/** OEE 计算入参 */
export interface MesOeeInput {
  equipmentId: string;
  shiftDate: string;
  shiftCode: string;
  plannedMinutes: number;
  plannedDowntimeMinutes?: number;
  runMinutes: number;
  downtimeMinutes?: number;
  totalCount: number;
  goodCount: number;
  idealCycleSeconds: number;
  formulaConfig?: string;
  remark?: string;
}

/** OEE 查询条件 */
export interface MesOeeQuery {
  equipmentId?: string;
  equipmentCode?: string;
  shiftDate?: string;
  shiftCode?: string;
  current?: number;
  size?: number;
}

/** 分页查询设备台账 */
export function pageMesEquipments(query: MesEquipmentQuery = {}): Promise<PageResult<MesEquipment>> {
  return http.get<PageResult<MesEquipment>>(
    `/mes/equipment/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 保存设备台账 */
export function saveMesEquipment(input: MesEquipmentInput): Promise<MesEquipment> {
  return http.post<MesEquipment>('/mes/equipment', input);
}

/** 设备开工门禁 */
export function fetchMesEquipmentGate(equipmentCode: string): Promise<MesEquipmentGate> {
  return http.get<MesEquipmentGate>(`/mes/equipment/gate?${toQueryString({ equipmentCode })}`);
}

/** 变更设备状态 */
export function changeMesEquipmentStatus(
  id: string,
  input: MesEquipmentStatusChangeInput,
): Promise<MesEquipmentStatusEvent> {
  return http.post<MesEquipmentStatusEvent>(`/mes/equipment/${id}/status`, input);
}

/** 分页查询设备状态事件 */
export function pageMesEquipmentStatusEvents(
  query: { equipmentId?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesEquipmentStatusEvent>> {
  return http.get<PageResult<MesEquipmentStatusEvent>>(
    `/mes/equipment/status-event/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 分页查询停机记录 */
export function pageMesEquipmentDowntimes(
  query: { equipmentId?: string; current?: number; size?: number } = {},
): Promise<PageResult<MesEquipmentDowntime>> {
  return http.get<PageResult<MesEquipmentDowntime>>(
    `/mes/equipment/downtime/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 保存保养计划 */
export function saveMesMaintenancePlan(input: MesMaintenancePlanInput): Promise<MesMaintenancePlan> {
  return http.post<MesMaintenancePlan>('/mes/equipment/maintenance/plan', input);
}

/** 分页查询保养计划 */
export function pageMesMaintenancePlans(
  query: MesMaintenanceQuery = {},
): Promise<PageResult<MesMaintenancePlan>> {
  return http.get<PageResult<MesMaintenancePlan>>(
    `/mes/equipment/maintenance/plan/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 生成保养工单 */
export function createMesMaintenanceOrder(input: MesMaintenanceOrderInput): Promise<MesMaintenanceOrder> {
  return http.post<MesMaintenanceOrder>('/mes/equipment/maintenance/order', input);
}

/** 分页查询保养工单 */
export function pageMesMaintenanceOrders(
  query: MesMaintenanceQuery = {},
): Promise<PageResult<MesMaintenanceOrder>> {
  return http.get<PageResult<MesMaintenanceOrder>>(
    `/mes/equipment/maintenance/order/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 开始保养 */
export function startMesMaintenanceOrder(id: string, assignee?: string): Promise<MesMaintenanceOrder> {
  return http.post<MesMaintenanceOrder>(`/mes/equipment/maintenance/order/${id}/start`, { assignee });
}

/** 保养完工 */
export function completeMesMaintenanceOrder(
  id: string,
  input: { completedBy?: string; checklistResults: string[]; remark?: string },
): Promise<MesMaintenanceOrder> {
  return http.post<MesMaintenanceOrder>(`/mes/equipment/maintenance/order/${id}/complete`, input);
}

/** 保养验收 */
export function acceptMesMaintenanceOrder(
  id: string,
  input: { acceptedBy?: string; conclusion: string },
): Promise<MesMaintenanceOrder> {
  return http.post<MesMaintenanceOrder>(`/mes/equipment/maintenance/order/${id}/accept`, input);
}

/** 创建维修工单 */
export function createMesRepair(input: MesRepairInput): Promise<MesRepairOrder> {
  return http.post<MesRepairOrder>('/mes/equipment/repair', input);
}

/** 分页查询维修工单 */
export function pageMesRepairs(query: MesRepairQuery = {}): Promise<PageResult<MesRepairOrder>> {
  return http.get<PageResult<MesRepairOrder>>(
    `/mes/equipment/repair/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 维修派工 */
export function assignMesRepair(id: string, input: MesRepairActionInput): Promise<MesRepairOrder> {
  return http.post<MesRepairOrder>(`/mes/equipment/repair/${id}/assign`, input);
}

/** 开始维修 */
export function startMesRepair(id: string, input: MesRepairActionInput): Promise<MesRepairOrder> {
  return http.post<MesRepairOrder>(`/mes/equipment/repair/${id}/start`, input);
}

/** 维修验证 */
export function verifyMesRepair(id: string, input: MesRepairActionInput): Promise<MesRepairOrder> {
  return http.post<MesRepairOrder>(`/mes/equipment/repair/${id}/verify`, input);
}

/** 关闭维修 */
export function closeMesRepair(id: string, input: MesRepairActionInput): Promise<MesRepairOrder> {
  return http.post<MesRepairOrder>(`/mes/equipment/repair/${id}/close`, input);
}

/** 保存备件主档 */
export function saveMesSparePart(input: MesSparePartInput): Promise<MesSparePart> {
  return http.post<MesSparePart>('/mes/equipment/spare-part', input);
}

/** 分页查询备件主档 */
export function pageMesSpareParts(query: MesSparePartQuery = {}): Promise<PageResult<MesSparePart>> {
  return http.get<PageResult<MesSparePart>>(
    `/mes/equipment/spare-part/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 分页查询备件库存 */
export function pageMesSparePartStocks(query: MesSparePartQuery = {}): Promise<PageResult<MesSparePartStock>> {
  return http.get<PageResult<MesSparePartStock>>(
    `/mes/equipment/spare-part/stock/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 分页查询备件流水 */
export function pageMesSparePartMovements(
  query: MesSparePartQuery = {},
): Promise<PageResult<MesSparePartMovement>> {
  return http.get<PageResult<MesSparePartMovement>>(
    `/mes/equipment/spare-part/movement/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 备件入库 */
export function stockInMesSparePart(input: MesSparePartStockInput): Promise<MesSparePartMovement> {
  return http.post<MesSparePartMovement>('/mes/equipment/spare-part/stock/in', input);
}

/** 备件领用 */
export function stockOutMesSparePart(input: MesSparePartStockInput): Promise<MesSparePartMovement> {
  return http.post<MesSparePartMovement>('/mes/equipment/spare-part/stock/out', input);
}

/** 计算 OEE 快照 */
export function computeMesOee(input: MesOeeInput): Promise<MesOeeSnapshot> {
  return http.post<MesOeeSnapshot>('/mes/equipment/oee/snapshot', input);
}

/** 分页查询 OEE 快照 */
export function pageMesOee(query: MesOeeQuery = {}): Promise<PageResult<MesOeeSnapshot>> {
  return http.get<PageResult<MesOeeSnapshot>>(`/mes/equipment/oee/page?${toQueryString({ current: 1, size: 200, ...query })}`);
}

/** 采集设备 */
export interface MesCollectDevice {
  id: string;
  deviceCode: string;
  deviceName: string;
  deviceType?: string;
  protocol?: string;
  ipAddress?: string;
  lineId?: string;
  lineCode?: string;
  status: string;
  lastHeartbeatAt?: string;
  remark?: string;
  createTime?: string;
}

/** 采集设备入参 */
export type MesCollectDeviceInput = Omit<MesCollectDevice, 'id' | 'lastHeartbeatAt' | 'createTime'> & {
  id?: string;
};

/** 采集设备查询条件 */
export interface MesCollectDeviceQuery {
  deviceCode?: string;
  deviceName?: string;
  deviceType?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 采集点 */
export interface MesCollectPoint {
  id: string;
  pointCode: string;
  pointName: string;
  deviceId?: string;
  deviceCode?: string;
  equipmentId?: string;
  equipmentCode?: string;
  operationCode?: string;
  metricType?: string;
  unit?: string;
  collectIntervalSeconds?: number;
  enabled?: number;
  remark?: string;
  createTime?: string;
}

/** 采集点入参 */
export type MesCollectPointInput = Omit<MesCollectPoint, 'id' | 'createTime'> & { id?: string };

/** 采集点查询条件 */
export interface MesCollectPointQuery {
  pointCode?: string;
  pointName?: string;
  equipmentId?: string;
  deviceId?: string;
  enabled?: number;
  current?: number;
  size?: number;
}

/** 采集样本 */
export interface MesMetricSample {
  id: string;
  pointId: string;
  pointCode: string;
  equipmentId?: string;
  equipmentCode?: string;
  metricValue: number | string;
  unit?: string;
  sampledAt?: string;
  seq?: number;
  payload?: string;
}

/** 采集样本查询条件 */
export interface MesMetricSampleQuery {
  pointId?: string;
  pointCode?: string;
  equipmentCode?: string;
  startTime?: string;
  endTime?: string;
  current?: number;
  size?: number;
}

/** 采集看板配置 */
export interface MesCollectBoard {
  id: string;
  boardCode: string;
  boardName: string;
  lineId?: string;
  lineCode?: string;
  refreshSeconds?: number;
  metricCodes?: string;
  layout?: string;
  status: string;
  remark?: string;
  createTime?: string;
}

/** 采集看板入参 */
export type MesCollectBoardInput = Omit<MesCollectBoard, 'id' | 'createTime'> & { id?: string };

/** 采集看板查询条件 */
export interface MesCollectBoardQuery {
  boardCode?: string;
  boardName?: string;
  lineId?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 看板实时数据 */
export interface MesCollectBoardData {
  board: MesCollectBoard;
  latestSamples: MesMetricSample[];
  generatedAt?: string;
}

/** 归档策略 */
export interface MesArchivePolicy {
  id: string;
  policyCode: string;
  tableName: string;
  retentionDays?: number;
  archiveBatchSize?: number;
  cronExpr?: string;
  status: string;
  lastRunAt?: string;
  lastCandidateCount?: number;
  remark?: string;
  createTime?: string;
}

/** 归档策略入参 */
export type MesArchivePolicyInput = Omit<MesArchivePolicy, 'id' | 'lastRunAt' | 'lastCandidateCount' | 'createTime'> & {
  id?: string;
};

/** 归档策略查询条件 */
export interface MesArchivePolicyQuery {
  policyCode?: string;
  tableName?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 分页查询采集设备 */
export function pageMesCollectDevices(
  query: MesCollectDeviceQuery = {},
): Promise<PageResult<MesCollectDevice>> {
  return http.get<PageResult<MesCollectDevice>>(
    `/mes/collect/device/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 保存采集设备 */
export function saveMesCollectDevice(input: MesCollectDeviceInput): Promise<MesCollectDevice> {
  return http.post<MesCollectDevice>('/mes/collect/device', input);
}

/** 采集设备心跳 */
export function heartbeatMesCollectDevice(deviceCode: string): Promise<MesCollectDevice> {
  return http.post<MesCollectDevice>(`/mes/collect/device/${deviceCode}/heartbeat`, {});
}

/** 分页查询采集点 */
export function pageMesCollectPoints(
  query: MesCollectPointQuery = {},
): Promise<PageResult<MesCollectPoint>> {
  return http.get<PageResult<MesCollectPoint>>(
    `/mes/collect/point/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 保存采集点 */
export function saveMesCollectPoint(input: MesCollectPointInput): Promise<MesCollectPoint> {
  return http.post<MesCollectPoint>('/mes/collect/point', input);
}

/** 分页查询采集样本 */
export function pageMesMetricSamples(
  query: MesMetricSampleQuery = {},
): Promise<PageResult<MesMetricSample>> {
  return http.get<PageResult<MesMetricSample>>(
    `/mes/collect/sample/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 分页查询采集看板 */
export function pageMesCollectBoards(
  query: MesCollectBoardQuery = {},
): Promise<PageResult<MesCollectBoard>> {
  return http.get<PageResult<MesCollectBoard>>(
    `/mes/collect/board/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 保存采集看板 */
export function saveMesCollectBoard(input: MesCollectBoardInput): Promise<MesCollectBoard> {
  return http.post<MesCollectBoard>('/mes/collect/board', input);
}

/** 查询看板实时数据 */
export function fetchMesCollectBoardData(boardCode: string): Promise<MesCollectBoardData> {
  return http.get<MesCollectBoardData>(`/mes/collect/board/${boardCode}/data`);
}

/** 分页查询归档策略 */
export function pageMesArchivePolicies(
  query: MesArchivePolicyQuery = {},
): Promise<PageResult<MesArchivePolicy>> {
  return http.get<PageResult<MesArchivePolicy>>(
    `/mes/collect/archive/page?${toQueryString({ current: 1, size: 200, ...query })}`,
  );
}

/** 保存归档策略 */
export function saveMesArchivePolicy(input: MesArchivePolicyInput): Promise<MesArchivePolicy> {
  return http.post<MesArchivePolicy>('/mes/collect/archive', input);
}

/** 执行归档 */
export function runMesArchivePolicy(id: string): Promise<MesArchivePolicy> {
  return http.post<MesArchivePolicy>(`/mes/collect/archive/${id}/run`, {});
}

// =====================================================================
// 人员绩效（M5）：班组成员 / 排班分配 / 交接班 / 工时 / 绩效 / 技能矩阵
// 接口统一挂载在 /mes/workforce，字段与后端 DTO/VO 保持一致。
// =====================================================================

/** 班组成员状态 */
export type MesTeamMemberStatus = 'ENABLED' | 'DISABLED';

/** 技能等级 */
export type MesSkillLevel = 'JUNIOR' | 'MIDDLE' | 'SENIOR' | 'EXPERT';

/** 班组成员 */
export interface MesTeamMember {
  id: number;
  /** 班组 ID */
  teamId: number;
  /** 班组编码 */
  teamCode?: string;
  /** 员工 ID */
  employeeId: number;
  /** 员工姓名 */
  employeeName: string;
  /** 是否主操作员：0 否 / 1 是 */
  primaryOperator?: number;
  /** 技能等级 */
  skillLevel?: string;
  /** 入组日期 */
  joinDate?: string;
  /** 离组日期 */
  leaveDate?: string;
  /** 状态 */
  status: string;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 班组成员新增/编辑入参 */
export interface MesTeamMemberInput {
  id?: number;
  teamId: number;
  employeeId: number;
  employeeName: string;
  primaryOperator?: number;
  skillLevel?: string;
  joinDate?: string;
  leaveDate?: string;
  status?: string;
  remark?: string;
}

/** 班组成员查询条件 */
export interface MesTeamMemberQuery {
  teamId?: number;
  employeeId?: number;
  employeeName?: string;
  status?: string;
  primaryOperator?: number;
  current?: number;
  size?: number;
}

/** 分页查询班组成员 */
export function pageMesTeamMembers(
  query: MesTeamMemberQuery = {},
): Promise<PageResult<MesTeamMember>> {
  return http.get<PageResult<MesTeamMember>>(
    `/mes/workforce/team-member/page?${toQueryString({ current: 1, size: 10, ...query })}`,
  );
}

/** 保存班组成员（无 id 新增，有 id 编辑） */
export function saveMesTeamMember(input: MesTeamMemberInput): Promise<MesTeamMember> {
  return http.post<MesTeamMember>('/mes/workforce/team-member', input);
}

/** 启停班组成员 */
export function changeMesTeamMemberStatus(
  id: number,
  enabled: boolean,
): Promise<MesTeamMember> {
  return http.post<MesTeamMember>(`/mes/workforce/team-member/${id}/status`, { enabled });
}

/** 排班分配 */
export interface MesShiftAssignment {
  id: number;
  /** 排班日期 */
  shiftDate: string;
  /** 班次编码 */
  shiftCode: string;
  /** 班组 ID */
  teamId: number;
  /** 班组编码 */
  teamCode?: string;
  /** 工位 ID */
  stationId: number;
  /** 工位编码 */
  stationCode?: string;
  /** 工作中心 ID */
  workCenterId?: number;
  /** 工作中心编码 */
  workCenterCode?: string;
  /** 员工 ID */
  employeeId: number;
  /** 员工姓名 */
  employeeName: string;
  /** 是否主操作：0 否 / 1 是 */
  primaryOperator?: number;
  /** 班次开始时间 */
  startAt?: string;
  /** 班次结束时间 */
  endAt?: string;
  /** 状态：ASSIGNED / CANCELLED */
  status: string;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 排班分配新增/编辑入参 */
export interface MesShiftAssignmentInput {
  id?: number;
  shiftDate: string;
  shiftCode: string;
  teamId: number;
  stationId: number;
  workCenterId?: number;
  employeeId: number;
  employeeName: string;
  primaryOperator?: number;
  startAt?: string;
  endAt?: string;
  remark?: string;
}

/** 排班分配查询条件 */
export interface MesShiftAssignmentQuery {
  shiftDate?: string;
  shiftCode?: string;
  teamId?: number;
  stationId?: number;
  employeeId?: number;
  status?: string;
  current?: number;
  size?: number;
}

/** 分页查询排班分配 */
export function pageMesShiftAssignments(
  query: MesShiftAssignmentQuery = {},
): Promise<PageResult<MesShiftAssignment>> {
  return http.get<PageResult<MesShiftAssignment>>(
    `/mes/workforce/shift-assignment/page?${toQueryString({ current: 1, size: 10, ...query })}`,
  );
}

/** 保存排班分配（无 id 新建，有 id 编辑） */
export function saveMesShiftAssignment(
  input: MesShiftAssignmentInput,
): Promise<MesShiftAssignment> {
  return http.post<MesShiftAssignment>('/mes/workforce/shift-assignment', input);
}

/** 取消排班分配 */
export function cancelMesShiftAssignment(id: number): Promise<MesShiftAssignment> {
  return http.post<MesShiftAssignment>(`/mes/workforce/shift-assignment/${id}/cancel`, {});
}

/** 交接班 */
export interface MesHandover {
  id: number;
  /** 交接单号 */
  handoverNo: string;
  /** 班组 ID */
  teamId: number;
  /** 班组编码 */
  teamCode?: string;
  /** 交班班次编码 */
  fromShiftCode: string;
  /** 接班班次编码 */
  toShiftCode: string;
  /** 交接日期 */
  handoverDate: string;
  /** 交班人 ID */
  fromEmployeeId?: number;
  /** 交班人姓名 */
  fromEmployeeName?: string;
  /** 接班人 ID */
  toEmployeeId?: number;
  /** 接班人姓名 */
  toEmployeeName?: string;
  /** 交接内容 */
  content: string;
  /** 遗留问题 */
  pendingIssues?: string;
  /** 状态：DRAFT / CONFIRMED */
  status: string;
  /** 确认人 */
  confirmedBy?: string;
  /** 确认时间 */
  confirmedAt?: string;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 交接班新建入参 */
export interface MesHandoverInput {
  handoverNo?: string;
  teamId: number;
  fromShiftCode: string;
  toShiftCode: string;
  handoverDate: string;
  fromEmployeeId?: number;
  fromEmployeeName?: string;
  toEmployeeId?: number;
  toEmployeeName?: string;
  content: string;
  pendingIssues?: string;
  remark?: string;
}

/** 交接班查询条件 */
export interface MesHandoverQuery {
  handoverNo?: string;
  teamId?: number;
  handoverDate?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 分页查询交接班 */
export function pageMesHandovers(
  query: MesHandoverQuery = {},
): Promise<PageResult<MesHandover>> {
  return http.get<PageResult<MesHandover>>(
    `/mes/workforce/handover/page?${toQueryString({ current: 1, size: 10, ...query })}`,
  );
}

/** 新建交接班 */
export function createMesHandover(input: MesHandoverInput): Promise<MesHandover> {
  return http.post<MesHandover>('/mes/workforce/handover', input);
}

/** 确认交接班（DRAFT → CONFIRMED） */
export function confirmMesHandover(id: number, confirmedBy: string): Promise<MesHandover> {
  return http.post<MesHandover>(`/mes/workforce/handover/${id}/confirm`, { confirmedBy });
}

/** 工时记录 */
export interface MesWorkHour {
  id: number;
  /** 员工 ID */
  employeeId: number;
  /** 员工姓名 */
  employeeName: string;
  /** 工作日期 */
  workDate: string;
  /** 任务 ID */
  taskId: number;
  /** 任务编号 */
  taskNo?: string;
  /** 工单 ID */
  workOrderId?: number;
  /** 工单编码 */
  workOrderCode?: string;
  /** 开始时间 */
  startAt?: string;
  /** 结束时间 */
  endAt?: string;
  /** 暂停秒数 */
  pauseSeconds?: number;
  /** 净工时秒数 */
  netSeconds?: number;
  /** 来源：REPORT / MANUAL */
  source?: string;
  /** 状态：VALID / VOID */
  status: string;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 工时登记入参 */
export interface MesWorkHourInput {
  employeeId: number;
  employeeName: string;
  workDate: string;
  taskId: number;
  startAt: string;
  endAt: string;
  pauseSeconds?: number;
  source?: string;
  remark?: string;
}

/** 工时查询条件 */
export interface MesWorkHourQuery {
  employeeId?: number;
  workDate?: string;
  taskId?: number;
  source?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 分页查询工时记录 */
export function pageMesWorkHours(
  query: MesWorkHourQuery = {},
): Promise<PageResult<MesWorkHour>> {
  return http.get<PageResult<MesWorkHour>>(
    `/mes/workforce/work-hour/page?${toQueryString({ current: 1, size: 10, ...query })}`,
  );
}

/** 登记工时 */
export function registerMesWorkHour(input: MesWorkHourInput): Promise<MesWorkHour> {
  return http.post<MesWorkHour>('/mes/workforce/work-hour', input);
}

/** 作废工时记录 */
export function voidMesWorkHour(id: number): Promise<MesWorkHour> {
  return http.post<MesWorkHour>(`/mes/workforce/work-hour/${id}/void`, {});
}

/** 绩效统计层级 */
export type MesPerformanceLevel = 'DAILY' | 'MONTHLY';

/** 绩效统计记录 */
export interface MesPerformanceStat {
  id: number;
  /** 统计层级 */
  statLevel: string;
  /** 员工 ID */
  employeeId: number;
  /** 员工姓名 */
  employeeName?: string;
  /** 班组 ID */
  teamId?: number;
  /** 班组编码 */
  teamCode?: string;
  /** 统计日期 */
  workDate?: string;
  /** 期间编码：日 YYYYMMDD / 月 YYYYMM */
  periodCode: string;
  /** 产量 */
  productionQuantity?: number | string;
  /** 合格数量 */
  qualifiedQuantity?: number | string;
  /** 不良数量 */
  defectQuantity?: number | string;
  /** 净工时秒数 */
  workSeconds?: number;
  /** 标准工时秒数 */
  standardSeconds?: number;
  /** 效率 */
  efficiency?: number | string;
  /** 合格率 */
  passRate?: number | string;
  /** 月度锁定：0 否 / 1 是 */
  locked?: number;
  /** 锁定时间 */
  lockedAt?: string;
  /** 锁定人 */
  lockedBy?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 绩效统计查询条件 */
export interface MesPerformanceQuery {
  statLevel?: string;
  employeeId?: number;
  teamId?: number;
  periodCode?: string;
  workDate?: string;
  locked?: number;
  current?: number;
  size?: number;
}

/** 绩效汇总入参 */
export interface MesPerformanceAggregateInput {
  statLevel?: string;
  periodCode: string;
  teamId?: number;
}

/** 月度绩效锁定入参 */
export interface MesPerformanceLockInput {
  periodCode: string;
  lockedBy: string;
  teamId?: number;
}

/** 月度绩效锁定结果 */
export interface MesPerformanceLockResult {
  periodCode: string;
  lockedCount: number;
  alreadyLockedCount: number;
  lockedAt?: string;
}

/** 分页查询绩效统计 */
export function pageMesPerformance(
  query: MesPerformanceQuery = {},
): Promise<PageResult<MesPerformanceStat>> {
  return http.get<PageResult<MesPerformanceStat>>(
    `/mes/workforce/performance/page?${toQueryString({ current: 1, size: 10, ...query })}`,
  );
}

/** 按期间汇总绩效统计 */
export function aggregateMesPerformance(
  input: MesPerformanceAggregateInput,
): Promise<MesPerformanceStat[]> {
  return http.post<MesPerformanceStat[]>('/mes/workforce/performance/aggregate', input);
}

/** 锁定月度绩效 */
export function lockMesPerformance(
  input: MesPerformanceLockInput,
): Promise<MesPerformanceLockResult> {
  return http.post<MesPerformanceLockResult>('/mes/workforce/performance/lock', input);
}

/** 技能矩阵记录 */
export interface MesSkillMatrix {
  id: number;
  /** 员工 ID */
  employeeId: number;
  /** 员工姓名 */
  employeeName: string;
  /** 技能编码 */
  skillCode: string;
  /** 技能名称 */
  skillName: string;
  /** 技能等级 */
  skillLevel?: string;
  /** 认证日期 */
  certifiedAt?: string;
  /** 失效日期 */
  expiryAt?: string;
  /** 状态：ENABLED / EXPIRED / DISABLED */
  status: string;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 技能矩阵新增/编辑入参 */
export interface MesSkillMatrixInput {
  id?: number;
  employeeId: number;
  employeeName: string;
  skillCode: string;
  skillName: string;
  skillLevel?: string;
  certifiedAt?: string;
  expiryAt?: string;
  status?: string;
  remark?: string;
}

/** 技能矩阵查询条件 */
export interface MesSkillMatrixQuery {
  employeeId?: number;
  skillCode?: string;
  skillLevel?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 分页查询技能矩阵 */
export function pageMesSkills(
  query: MesSkillMatrixQuery = {},
): Promise<PageResult<MesSkillMatrix>> {
  return http.get<PageResult<MesSkillMatrix>>(
    `/mes/workforce/skill/page?${toQueryString({ current: 1, size: 10, ...query })}`,
  );
}

/** 保存技能矩阵记录（无 id 新增，有 id 编辑） */
export function saveMesSkillMatrix(input: MesSkillMatrixInput): Promise<MesSkillMatrix> {
  return http.post<MesSkillMatrix>('/mes/workforce/skill', input);
}

