// ERP 生产计划 API：需求预测、主生产计划、生产订单（领料/完工入库）
import { http } from './client';
import type { PageResult } from './masterdata';

/** 需求预测分录 */
export interface ForecastLineView {
  lineNo: number;
  itemId?: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  quantity: number;
}

/** 需求预测批次 */
export interface ForecastView {
  id: number;
  batchNo: string;
  period: string;
  version: number;
  status: string;
  totalQuantity: number;
  remark?: string;
  lines?: ForecastLineView[];
}

/** 需求预测入参 */
export interface ForecastInput {
  period: string;
  version?: number;
  remark?: string;
  lines: { itemCode: string; quantity: number; uom?: string }[];
}

/** 主生产计划分录 */
export interface MpsLineView {
  lineNo: number;
  itemId?: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  plannedQuantity: number;
  releasedQuantity?: number;
}

/** 主生产计划 */
export interface MpsPlanView {
  id: number;
  planNo: string;
  period: string;
  status: string;
  totalPlanned: number;
  remark?: string;
  lines?: MpsLineView[];
}

/** 主生产计划入参 */
export interface MpsInput {
  period: string;
  remark?: string;
  lines: { itemCode: string; plannedQuantity: number; uom?: string }[];
}

/** 生产订单用料 */
export interface ProductionMaterialView {
  lineNo: number;
  itemId?: number;
  itemCode: string;
  itemName?: string;
  uom?: string;
  requiredQuantity: number;
  issuedQuantity?: number;
}

/** 生产订单 */
export interface ProductionOrderView {
  id: number;
  orderNo: string;
  itemId?: number;
  itemCode: string;
  itemName?: string;
  plannedQuantity: number;
  completedQuantity?: number;
  completedCost?: number;
  totalMaterialCost?: number;
  issueWarehouseId: number;
  issueWarehouseName?: string;
  targetWarehouseId: number;
  targetWarehouseName?: string;
  planStartDate: string;
  planEndDate?: string;
  completeDate?: string;
  status: string;
  remark?: string;
  materials?: ProductionMaterialView[];
}

/** 生产订单入参 */
export interface ProductionOrderInput {
  itemCode: string;
  plannedQuantity: number;
  issueWarehouseId: number;
  targetWarehouseId: number;
  planStartDate: string;
  planEndDate?: string;
  remark?: string;
  materials: { itemCode: string; requiredQuantity: number; uom?: string }[];
}

/** 生产计划查询参数 */
export interface MfgQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  period?: string;
  itemId?: number;
  warehouseId?: number;
  fromDate?: string;
  toDate?: string;
}

/** 构造查询串（空值不下发，默认每页 20 条） */
function buildQuery(params: MfgQueryParams): string {
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

/** 需求预测分页 */
export function pageForecasts(params: MfgQueryParams = {}): Promise<PageResult<ForecastView>> {
  return http.get<PageResult<ForecastView>>(`/erp/mfg/forecast/page?${buildQuery(params)}`);
}

/** 需求预测详情 */
export function getForecast(id: number): Promise<ForecastView> {
  return http.get<ForecastView>(`/erp/mfg/forecast/${id}`);
}

/** 新增需求预测 */
export function createForecast(input: ForecastInput): Promise<ForecastView> {
  return http.post<ForecastView>('/erp/mfg/forecast', input);
}

/** 发布需求预测 */
export function publishForecast(id: number): Promise<ForecastView> {
  return http.post<ForecastView>(`/erp/mfg/forecast/${id}/publish`);
}

/** 取消需求预测 */
export function cancelForecast(id: number): Promise<ForecastView> {
  return http.post<ForecastView>(`/erp/mfg/forecast/${id}/cancel`);
}

/** 主生产计划分页 */
export function pageMpsPlans(params: MfgQueryParams = {}): Promise<PageResult<MpsPlanView>> {
  return http.get<PageResult<MpsPlanView>>(`/erp/mfg/mps/page?${buildQuery(params)}`);
}

/** 主生产计划详情 */
export function getMpsPlan(id: number): Promise<MpsPlanView> {
  return http.get<MpsPlanView>(`/erp/mfg/mps/${id}`);
}

/** 新增主生产计划 */
export function createMpsPlan(input: MpsInput): Promise<MpsPlanView> {
  return http.post<MpsPlanView>('/erp/mfg/mps', input);
}

/** 下达主生产计划 */
export function releaseMpsPlan(id: number): Promise<MpsPlanView> {
  return http.post<MpsPlanView>(`/erp/mfg/mps/${id}/release`);
}

/** 取消主生产计划 */
export function cancelMpsPlan(id: number): Promise<MpsPlanView> {
  return http.post<MpsPlanView>(`/erp/mfg/mps/${id}/cancel`);
}

/** 生产订单分页 */
export function pageProductionOrders(
  params: MfgQueryParams = {},
): Promise<PageResult<ProductionOrderView>> {
  return http.get<PageResult<ProductionOrderView>>(
    `/erp/mfg/production/page?${buildQuery(params)}`,
  );
}

/** 生产订单详情（含用料） */
export function getProductionOrder(id: number): Promise<ProductionOrderView> {
  return http.get<ProductionOrderView>(`/erp/mfg/production/${id}`);
}

/** 新增生产订单 */
export function createProductionOrder(
  input: ProductionOrderInput,
): Promise<ProductionOrderView> {
  return http.post<ProductionOrderView>('/erp/mfg/production', input);
}

/** 下达生产订单 */
export function releaseProductionOrder(id: number): Promise<ProductionOrderView> {
  return http.post<ProductionOrderView>(`/erp/mfg/production/${id}/release`);
}

/** 生产领料 */
export function issueProduction(
  id: number,
  input: { lines: { lineNo: number; quantity: number }[] },
): Promise<ProductionOrderView> {
  return http.post<ProductionOrderView>(`/erp/mfg/production/${id}/issue`, input);
}

/** 完工入库 */
export function completeProduction(
  id: number,
  input: { completeDate: string; quantity: number },
): Promise<ProductionOrderView> {
  return http.post<ProductionOrderView>(`/erp/mfg/production/${id}/complete`, input);
}

/** 生产订单结案 */
export function closeProductionOrder(id: number): Promise<ProductionOrderView> {
  return http.post<ProductionOrderView>(`/erp/mfg/production/${id}/close`);
}

/** 取消生产订单 */
export function cancelProductionOrder(id: number): Promise<ProductionOrderView> {
  return http.post<ProductionOrderView>(`/erp/mfg/production/${id}/cancel`);
}
