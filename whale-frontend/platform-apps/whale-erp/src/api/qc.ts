// ERP 质量与设备 API：检验单/NCR/质量成本、设备/保养/OEE、量具/校准
import { http } from './client';
import type { PageResult } from './masterdata';

/** 质量与设备查询参数 */
export interface QcQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  inspectionType?: string;
  sourceType?: string;
  itemCode?: string;
  lotNo?: string;
  supplierCode?: string;
  equipmentNo?: string;
  toolNo?: string;
  costType?: string;
  fromDate?: string;
  toDate?: string;
}

/** 检验项 */
export interface InspectionItemView {
  lineNo: number;
  itemName: string;
  standardValue?: string;
  actualValue?: string;
  result?: string;
  remark?: string;
}

/** 检验单 */
export interface InspectionView {
  id: number;
  orderNo: string;
  inspectionType: string;
  sourceType?: string;
  sourceNo: string;
  itemId?: number;
  itemCode: string;
  itemName?: string;
  lotNo?: string;
  quantity: number;
  inspectedQuantity: number;
  supplierCode?: string;
  status: string;
  inspector?: string;
  inspectDate: string;
  toolId?: number;
  toolNo?: string;
  ncrId?: number;
  remark?: string;
  items?: InspectionItemView[];
}

/** 检验单新建入参 */
export interface InspectionCreateInput {
  inspectionType: string;
  sourceType?: string;
  sourceNo: string;
  itemCode: string;
  lotNo?: string;
  quantity: number;
  supplierCode?: string;
  inspector?: string;
  inspectDate: string;
  toolNo?: string;
  remark?: string;
  items: Array<{ itemName: string; standardValue?: string }>;
}

/** 检验项录入行 */
export interface InspectionRecordLine {
  lineNo: number;
  actualValue?: string;
  result: string;
  remark?: string;
}

/** 检验项录入入参 */
export interface InspectionRecordInput {
  lines: InspectionRecordLine[];
}

/** 不合格品单 */
export interface NcrView {
  id: number;
  ncrNo: string;
  sourceType?: string;
  sourceNo: string;
  itemId?: number;
  itemName: string;
  lotNo?: string;
  quantity: number;
  defectDesc: string;
  disposition?: string;
  status: string;
  costAmount: number;
  disposeDate?: string;
  closeDate?: string;
  remark?: string;
}

/** 不合格品登记入参 */
export interface NcrCreateInput {
  sourceType?: string;
  sourceNo: string;
  itemName: string;
  lotNo?: string;
  quantity?: number;
  defectDesc: string;
  remark?: string;
}

/** 不合格品处置入参 */
export interface NcrDisposeInput {
  disposition: string;
  disposeDate: string;
  costAmount?: number;
  remark?: string;
}

/** 不合格品关闭入参 */
export interface NcrCloseInput {
  closeDate: string;
  remark?: string;
}

/** 质量成本 */
export interface QualityCostView {
  id: number;
  costNo: string;
  sourceType?: string;
  sourceNo?: string;
  costType: string;
  amount: number;
  costDate: string;
  remark?: string;
}

/** 设备档案 */
export interface EquipmentView {
  id: number;
  equipmentNo: string;
  name: string;
  equipmentType?: string;
  location?: string;
  status: string;
  purchaseDate?: string;
  remark?: string;
}

/** 设备保存入参 */
export interface EquipmentInput {
  equipmentNo: string;
  name: string;
  equipmentType?: string;
  location?: string;
  status?: string;
  purchaseDate?: string;
  remark?: string;
}

/** 保养计划 */
export interface MaintenancePlanView {
  id: number;
  planNo: string;
  equipmentNo: string;
  cycleDays: number;
  lastDate?: string;
  nextDate?: string;
  status: string;
  remark?: string;
}

/** 保养计划保存入参 */
export interface MaintenancePlanInput {
  equipmentNo: string;
  cycleDays: number;
  lastDate?: string;
  nextDate?: string;
  status?: string;
  remark?: string;
}

/** 保养工单 */
export interface MaintenanceOrderView {
  id: number;
  orderNo: string;
  equipmentNo: string;
  planId?: number;
  maintenanceType: string;
  status: string;
  planDate: string;
  completeDate?: string;
  costAmount: number;
  result?: string;
  remark?: string;
}

/** 保养工单新建入参 */
export interface MaintenanceOrderCreateInput {
  equipmentNo: string;
  planId?: number;
  maintenanceType: string;
  planDate: string;
  remark?: string;
}

/** 保养工单完成入参 */
export interface MaintenanceCompleteInput {
  completeDate: string;
  costAmount?: number;
  result?: string;
  remark?: string;
}

/** OEE 记录 */
export interface OeeRecordView {
  id: number;
  equipmentNo: string;
  recordDate: string;
  plannedMinutes: number;
  runMinutes: number;
  downMinutes: number;
  idealQty: number;
  outputQty: number;
  goodQty: number;
  availabilityRate: number;
  performanceRate: number;
  qualityRate: number;
  oeeRate: number;
}

/** OEE 回传入参（同设备同日期幂等覆盖） */
export interface OeeRecordInput {
  equipmentNo: string;
  recordDate: string;
  plannedMinutes: number;
  runMinutes?: number;
  downMinutes?: number;
  idealQty: number;
  outputQty?: number;
  goodQty?: number;
}

/** 量具台账 */
export interface MeasuringToolView {
  id: number;
  toolNo: string;
  name: string;
  toolType?: string;
  precisionValue?: string;
  calibrationCycleMonths: number;
  lastCalibrationDate?: string;
  nextCalibrationDate?: string;
  status: string;
  remark?: string;
}

/** 量具保存入参 */
export interface MeasuringToolInput {
  toolNo: string;
  name: string;
  toolType?: string;
  precisionValue?: string;
  calibrationCycleMonths: number;
  lastCalibrationDate?: string;
  nextCalibrationDate?: string;
  status?: string;
  remark?: string;
}

/** 校准计划 */
export interface CalibrationPlanView {
  id: number;
  planNo: string;
  toolNo: string;
  cycleMonths: number;
  nextDate: string;
  status: string;
  remark?: string;
}

/** 校准计划保存入参 */
export interface CalibrationPlanInput {
  toolNo: string;
  cycleMonths: number;
  nextDate: string;
  status?: string;
  remark?: string;
}

/** 校准记录 */
export interface CalibrationRecordView {
  id: number;
  recordNo: string;
  toolNo: string;
  planId?: number;
  calibrationDate: string;
  result: string;
  certificateNo?: string;
  remark?: string;
}

/** 校准登记入参 */
export interface CalibrationRecordInput {
  toolNo: string;
  planId?: number;
  calibrationDate: string;
  result: string;
  certificateNo?: string;
  remark?: string;
}

/** 检验状态中文标签 */
export const INSPECTION_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  INSPECTING: '检验中',
  PASSED: '合格',
  FAILED: '不合格',
  CANCELLED: '已取消',
};

/** 检验类型中文标签 */
export const INSPECTION_TYPE_LABEL: Record<string, string> = {
  IQC: '来料检验',
  IPQC: '过程检验',
  FQC: '成品检验',
};

/** 来源类型中文标签 */
export const QC_SOURCE_LABEL: Record<string, string> = {
  PURCHASE_RECEIPT: '采购收货',
  PRODUCTION_ORDER: '生产订单',
  MANUAL: '手工建单',
  INSPECTION: '检验单',
  NCR: '不合格品单',
};

/** 检验结论/校准结论中文标签 */
export const QC_RESULT_LABEL: Record<string, string> = {
  PASS: '合格',
  FAIL: '不合格',
};

/** 不合格品状态中文标签 */
export const NCR_STATUS_LABEL: Record<string, string> = {
  OPEN: '待处置',
  DISPOSED: '已处置',
  CLOSED: '已关闭',
};

/** 处置方式中文标签 */
export const DISPOSITION_LABEL: Record<string, string> = {
  REWORK: '返工',
  CONCESSION: '让步接收',
  SCRAP: '报废',
  RETURN: '退货',
};

/** 质量成本类型中文标签 */
export const COST_TYPE_LABEL: Record<string, string> = {
  REWORK: '返工成本',
  SCRAP: '报废成本',
  INSPECTION: '检验成本',
};

/** 设备状态中文标签 */
export const EQUIPMENT_STATUS_LABEL: Record<string, string> = {
  RUNNING: '运行中',
  IDLE: '闲置',
  DOWN: '故障停机',
  RETIRED: '已退役',
};

/** 启用状态中文标签 */
export const ENABLED_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

/** 保养类型中文标签 */
export const MAINTENANCE_TYPE_LABEL: Record<string, string> = {
  PREVENTIVE: '预防性保养',
  CORRECTIVE: '故障性保养',
};

/** 保养工单状态中文标签 */
export const MAINTENANCE_STATUS_LABEL: Record<string, string> = {
  OPEN: '待执行',
  IN_PROGRESS: '执行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
};

/** 质量状态颜色 */
export function qcStatusColor(status: string): string {
  if (['PASSED', 'COMPLETED', 'CLOSED', 'RUNNING', 'ACTIVE'].includes(status)) return 'green';
  if (['INSPECTING', 'IN_PROGRESS', 'DISPOSED'].includes(status)) return 'blue';
  if (['FAILED', 'DOWN'].includes(status)) return 'red';
  if (['DRAFT', 'OPEN'].includes(status)) return 'orange';
  return 'default';
}

/** 构造查询串（空值不下发，默认每页 20 条） */
function buildQuery(params: QcQueryParams): string {
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

/** 检验单分页 */
export function pageInspections(params: QcQueryParams = {}): Promise<PageResult<InspectionView>> {
  return http.get<PageResult<InspectionView>>(`/erp/v1/qc/inspection/page?${buildQuery(params)}`);
}

/** 检验单详情 */
export function getInspection(id: number): Promise<InspectionView> {
  return http.get<InspectionView>(`/erp/v1/qc/inspection/${id}`);
}

/** 新增检验单 */
export function createInspection(input: InspectionCreateInput): Promise<InspectionView> {
  return http.post<InspectionView>('/erp/v1/qc/inspection', input);
}

/** 录入检验项 */
export function recordInspection(
  id: number,
  input: InspectionRecordInput,
): Promise<InspectionView> {
  return http.post<InspectionView>(`/erp/v1/qc/inspection/${id}/record`, input);
}

/** 判定检验单（不合格自动生成 NCR） */
export function judgeInspection(id: number): Promise<InspectionView> {
  return http.post<InspectionView>(`/erp/v1/qc/inspection/${id}/judge`);
}

/** 不合格品单分页 */
export function pageNcr(params: QcQueryParams = {}): Promise<PageResult<NcrView>> {
  return http.get<PageResult<NcrView>>(`/erp/v1/qc/ncr/page?${buildQuery(params)}`);
}

/** 手工登记不合格品单 */
export function createNcr(input: NcrCreateInput): Promise<NcrView> {
  return http.post<NcrView>('/erp/v1/qc/ncr', input);
}

/** 处置不合格品单（登记成本自动生成质量成本） */
export function disposeNcr(id: number, input: NcrDisposeInput): Promise<NcrView> {
  return http.post<NcrView>(`/erp/v1/qc/ncr/${id}/dispose`, input);
}

/** 关闭不合格品单 */
export function closeNcr(id: number, input: NcrCloseInput): Promise<NcrView> {
  return http.post<NcrView>(`/erp/v1/qc/ncr/${id}/close`, input);
}

/** 质量成本分页 */
export function pageQualityCost(params: QcQueryParams = {}): Promise<PageResult<QualityCostView>> {
  return http.get<PageResult<QualityCostView>>(
    `/erp/v1/qc/quality-cost/page?${buildQuery(params)}`,
  );
}

/** 设备分页 */
export function pageEquipment(params: QcQueryParams = {}): Promise<PageResult<EquipmentView>> {
  return http.get<PageResult<EquipmentView>>(`/erp/v1/qc/equipment/page?${buildQuery(params)}`);
}

/** 新增设备档案 */
export function createEquipment(input: EquipmentInput): Promise<EquipmentView> {
  return http.post<EquipmentView>('/erp/v1/qc/equipment', input);
}

/** 编辑设备档案 */
export function updateEquipment(
  equipmentNo: string,
  input: EquipmentInput,
): Promise<EquipmentView> {
  return http.put<EquipmentView>(`/erp/v1/qc/equipment/${equipmentNo}`, input);
}

/** 保养计划分页 */
export function pageMaintenancePlans(
  params: QcQueryParams = {},
): Promise<PageResult<MaintenancePlanView>> {
  return http.get<PageResult<MaintenancePlanView>>(
    `/erp/v1/qc/maintenance/plan/page?${buildQuery(params)}`,
  );
}

/** 新增保养计划 */
export function createMaintenancePlan(input: MaintenancePlanInput): Promise<MaintenancePlanView> {
  return http.post<MaintenancePlanView>('/erp/v1/qc/maintenance/plan', input);
}

/** 编辑保养计划 */
export function updateMaintenancePlan(
  id: number,
  input: MaintenancePlanInput,
): Promise<MaintenancePlanView> {
  return http.put<MaintenancePlanView>(`/erp/v1/qc/maintenance/plan/${id}`, input);
}

/** 保养工单分页 */
export function pageMaintenanceOrders(
  params: QcQueryParams = {},
): Promise<PageResult<MaintenanceOrderView>> {
  return http.get<PageResult<MaintenanceOrderView>>(
    `/erp/v1/qc/maintenance/order/page?${buildQuery(params)}`,
  );
}

/** 新增保养工单 */
export function createMaintenanceOrder(
  input: MaintenanceOrderCreateInput,
): Promise<MaintenanceOrderView> {
  return http.post<MaintenanceOrderView>('/erp/v1/qc/maintenance/order', input);
}

/** 完成保养工单（回写计划下次保养日） */
export function completeMaintenanceOrder(
  id: number,
  input: MaintenanceCompleteInput,
): Promise<MaintenanceOrderView> {
  return http.post<MaintenanceOrderView>(`/erp/v1/qc/maintenance/order/${id}/complete`, input);
}

/** OEE 分页 */
export function pageOee(params: QcQueryParams = {}): Promise<PageResult<OeeRecordView>> {
  return http.get<PageResult<OeeRecordView>>(`/erp/v1/qc/oee/page?${buildQuery(params)}`);
}

/** 记录/回传 OEE 数据 */
export function recordOee(input: OeeRecordInput): Promise<OeeRecordView> {
  return http.post<OeeRecordView>('/erp/v1/qc/oee/record', input);
}

/** 量具分页 */
export function pageTools(params: QcQueryParams = {}): Promise<PageResult<MeasuringToolView>> {
  return http.get<PageResult<MeasuringToolView>>(
    `/erp/v1/qc/metrology/tool/page?${buildQuery(params)}`,
  );
}

/** 新增量具 */
export function createTool(input: MeasuringToolInput): Promise<MeasuringToolView> {
  return http.post<MeasuringToolView>('/erp/v1/qc/metrology/tool', input);
}

/** 编辑量具 */
export function updateTool(toolNo: string, input: MeasuringToolInput): Promise<MeasuringToolView> {
  return http.put<MeasuringToolView>(`/erp/v1/qc/metrology/tool/${toolNo}`, input);
}

/** 校准计划分页 */
export function pageCalibrationPlans(
  params: QcQueryParams = {},
): Promise<PageResult<CalibrationPlanView>> {
  return http.get<PageResult<CalibrationPlanView>>(
    `/erp/v1/qc/metrology/plan/page?${buildQuery(params)}`,
  );
}

/** 新增校准计划 */
export function createCalibrationPlan(input: CalibrationPlanInput): Promise<CalibrationPlanView> {
  return http.post<CalibrationPlanView>('/erp/v1/qc/metrology/plan', input);
}

/** 编辑校准计划 */
export function updateCalibrationPlan(
  id: number,
  input: CalibrationPlanInput,
): Promise<CalibrationPlanView> {
  return http.put<CalibrationPlanView>(`/erp/v1/qc/metrology/plan/${id}`, input);
}

/** 校准记录分页 */
export function pageCalibrationRecords(
  params: QcQueryParams = {},
): Promise<PageResult<CalibrationRecordView>> {
  return http.get<PageResult<CalibrationRecordView>>(
    `/erp/v1/qc/metrology/record/page?${buildQuery(params)}`,
  );
}

/** 登记校准记录（合格时顺延计划） */
export function calibrate(input: CalibrationRecordInput): Promise<CalibrationRecordView> {
  return http.post<CalibrationRecordView>('/erp/v1/qc/metrology/record', input);
}
