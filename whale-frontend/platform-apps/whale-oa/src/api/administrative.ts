// OA 行政 API 层：用车（车辆/司机/预约/派车/回车）、用章、采购
//
// 后端字段：时间为 ISO 字符串；车辆里程为 number；采购合计由服务端重算。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 用车/用章/采购通用审批状态 */
export type OaAdministrativeStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** ERP 采购推送状态 */
export type OaPurchasePushStatus = 'NONE' | 'PENDING' | 'SENT' | 'FAILED';

/** 车辆 */
export interface OaVehicle {
  id: number;
  plateNo: string;
  vehicleType?: string;
  seats?: number;
  status?: string;
  currentMileage?: number;
  purchaseDate?: string;
  remark?: string;
  createTime?: string;
}

/** 车辆入参 */
export interface OaVehicleInput {
  plateNo: string;
  vehicleType?: string;
  seats?: number;
  status?: string;
  currentMileage?: number;
  purchaseDate?: string;
  remark?: string;
}

/** 司机 */
export interface OaDriver {
  id: number;
  name: string;
  phone?: string;
  licenseNo: string;
  licenseType?: string;
  status?: string;
  remark?: string;
  createTime?: string;
}

/** 司机入参 */
export interface OaDriverInput {
  name: string;
  phone?: string;
  licenseNo: string;
  licenseType?: string;
  status?: string;
  remark?: string;
}

/** 用车申请 */
export interface OaVehicleBooking {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  deptId?: number;
  vehicleId?: number;
  driverId?: number;
  startTime: string;
  endTime: string;
  origin?: string;
  destination?: string;
  reason?: string;
  status: OaAdministrativeStatus;
  approvalInstanceId?: number;
  remark?: string;
  createTime?: string;
}

/** 用车申请入参 */
export interface OaVehicleBookingInput {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  startTime: string;
  endTime: string;
  origin?: string;
  destination?: string;
  reason?: string;
  remark?: string;
}

/** 出车登记 */
export interface OaVehicleUsage {
  id: number;
  bookingId: number;
  vehicleId?: number;
  startMileage?: number;
  endMileage?: number;
  fuelCost?: number;
  remark?: string;
  createTime?: string;
}

/** 回车登记入参 */
export interface OaVehicleReturnInput {
  startMileage: number;
  endMileage: number;
  fuelCost?: number;
  remark?: string;
}

/** 用章文件 */
export interface OaSealUseFile {
  id?: number;
  fileId?: number;
  fileName: string;
}

/** 用章申请 */
export interface OaSealUse {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  deptId?: number;
  sealId: number;
  sealName?: string;
  reason?: string;
  fileCount?: number;
  useTime?: string;
  status: OaAdministrativeStatus;
  registeredTime?: string;
  registeredBy?: number;
  approvalInstanceId?: number;
  remark?: string;
  files?: OaSealUseFile[];
  createTime?: string;
}

/** 用章申请入参 */
export interface OaSealUseInput {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  sealId: number;
  sealName?: string;
  reason: string;
  useTime?: string;
  remark?: string;
  files?: OaSealUseFile[];
}

/** Contract 可用印章 */
export interface OaSealInfo {
  id: number;
  name: string;
}

/** 采购明细 */
export interface OaPurchaseItem {
  id?: number;
  lineNo?: number;
  itemCode: string;
  itemName?: string;
  quantity: number;
  unitPrice: number;
  amount?: number;
  supplierAdvice?: string;
}

/** 采购申请 */
export interface OaPurchaseRequest {
  id: number;
  code: string;
  applicant: string;
  applicantId?: number;
  deptId?: number;
  reason: string;
  expectedDate?: string;
  supplierId?: number;
  warehouseId?: number;
  totalAmount?: number;
  currency?: string;
  status: OaAdministrativeStatus;
  approvalInstanceId?: number;
  erpPushStatus: OaPurchasePushStatus;
  erpPoNo?: string;
  remark?: string;
  items?: OaPurchaseItem[];
  createTime?: string;
}

/** 采购申请入参 */
export interface OaPurchaseInput {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  reason: string;
  expectedDate?: string;
  supplierId?: number;
  warehouseId?: number;
  currency?: string;
  remark?: string;
  items: OaPurchaseItem[];
}

/** 行政通用查询 */
export interface OaAdministrativeQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  applicantId?: number;
  vehicleId?: number;
  erpPushStatus?: string;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询车辆 */
export function pageVehicles(params: OaAdministrativeQuery = {}): Promise<PageResult<OaVehicle>> {
  return http.get<PageResult<OaVehicle>>(`/oa/v1/vehicle/page${buildQuery(params)}`);
}

/** 车辆详情 */
export function getVehicle(id: number): Promise<OaVehicle> {
  return http.get<OaVehicle>(`/oa/v1/vehicle/${id}`);
}

/** 新增车辆 */
export function createVehicle(input: OaVehicleInput): Promise<OaVehicle> {
  return http.post<OaVehicle>('/oa/v1/vehicle', input);
}

/** 更新车辆 */
export function updateVehicle(id: number, input: OaVehicleInput): Promise<OaVehicle> {
  return http.put<OaVehicle>(`/oa/v1/vehicle/${id}`, input);
}

/** 删除车辆 */
export function deleteVehicle(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/vehicle/${id}`);
}

/** 分页查询司机 */
export function pageDrivers(params: OaAdministrativeQuery = {}): Promise<PageResult<OaDriver>> {
  return http.get<PageResult<OaDriver>>(`/oa/v1/vehicle/drivers/page${buildQuery(params)}`);
}

/** 新增司机 */
export function createDriver(input: OaDriverInput): Promise<OaDriver> {
  return http.post<OaDriver>('/oa/v1/vehicle/drivers', input);
}

/** 更新司机 */
export function updateDriver(id: number, input: OaDriverInput): Promise<OaDriver> {
  return http.put<OaDriver>(`/oa/v1/vehicle/drivers/${id}`, input);
}

/** 删除司机 */
export function deleteDriver(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/vehicle/drivers/${id}`);
}

/** 分页查询用车申请 */
export function pageBookings(
  params: OaAdministrativeQuery = {},
): Promise<PageResult<OaVehicleBooking>> {
  return http.get<PageResult<OaVehicleBooking>>(
    `/oa/v1/vehicle/bookings/page${buildQuery(params)}`,
  );
}

/** 用车申请详情 */
export function getBooking(id: number): Promise<OaVehicleBooking> {
  return http.get<OaVehicleBooking>(`/oa/v1/vehicle/bookings/${id}`);
}

/** 新建用车申请 */
export function createBooking(input: OaVehicleBookingInput): Promise<OaVehicleBooking> {
  return http.post<OaVehicleBooking>('/oa/v1/vehicle/bookings', input);
}

/** 更新用车申请 */
export function updateBooking(id: number, input: OaVehicleBookingInput): Promise<OaVehicleBooking> {
  return http.put<OaVehicleBooking>(`/oa/v1/vehicle/bookings/${id}`, input);
}

/** 删除用车申请 */
export function deleteBooking(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/vehicle/bookings/${id}`);
}

/** 撤销用车申请 */
export function updateBookingStatus(id: number, status: OaAdministrativeStatus): Promise<void> {
  return http.put<void>(`/oa/v1/vehicle/bookings/${id}/status`, { status });
}

/** 派车 */
export function dispatchBooking(
  id: number,
  input: { vehicleId: number; driverId: number },
): Promise<OaVehicleBooking> {
  return http.post<OaVehicleBooking>(`/oa/v1/vehicle/bookings/${id}/dispatch`, input);
}

/** 回车登记 */
export function returnVehicle(id: number, input: OaVehicleReturnInput): Promise<OaVehicleUsage> {
  return http.post<OaVehicleUsage>(`/oa/v1/vehicle/bookings/${id}/return`, input);
}

/** 查询出车登记 */
export function getUsage(id: number): Promise<OaVehicleUsage> {
  return http.get<OaVehicleUsage>(`/oa/v1/vehicle/bookings/${id}/usage`);
}

/** 分页查询用章申请 */
export function pageSealUses(params: OaAdministrativeQuery = {}): Promise<PageResult<OaSealUse>> {
  return http.get<PageResult<OaSealUse>>(`/oa/v1/seal/page${buildQuery(params)}`);
}

/** 用章申请详情 */
export function getSealUse(id: number): Promise<OaSealUse> {
  return http.get<OaSealUse>(`/oa/v1/seal/${id}`);
}

/** 新建用章申请 */
export function createSealUse(input: OaSealUseInput): Promise<OaSealUse> {
  return http.post<OaSealUse>('/oa/v1/seal', input);
}

/** 更新用章申请 */
export function updateSealUse(id: number, input: OaSealUseInput): Promise<OaSealUse> {
  return http.put<OaSealUse>(`/oa/v1/seal/${id}`, input);
}

/** 删除用章申请 */
export function deleteSealUse(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/seal/${id}`);
}

/** 撤销用章申请 */
export function updateSealUseStatus(id: number, status: OaAdministrativeStatus): Promise<void> {
  return http.put<void>(`/oa/v1/seal/${id}/status`, { status });
}

/** 用印登记（审批通过后仅可登记一次） */
export function registerSealUse(id: number): Promise<OaSealUse> {
  return http.post<OaSealUse>(`/oa/v1/seal/${id}/register`);
}

/** 查询当前用户可用的印章（来自 Contract） */
export function listAvailableSeals(userId?: number): Promise<OaSealInfo[]> {
  return http.get<OaSealInfo[]>(`/oa/v1/seal/available-seals${buildQuery({ userId })}`);
}

/** 分页查询采购申请 */
export function pagePurchases(
  params: OaAdministrativeQuery = {},
): Promise<PageResult<OaPurchaseRequest>> {
  return http.get<PageResult<OaPurchaseRequest>>(`/oa/v1/purchase/page${buildQuery(params)}`);
}

/** 采购申请详情 */
export function getPurchase(id: number): Promise<OaPurchaseRequest> {
  return http.get<OaPurchaseRequest>(`/oa/v1/purchase/${id}`);
}

/** 新建采购申请 */
export function createPurchase(input: OaPurchaseInput): Promise<OaPurchaseRequest> {
  return http.post<OaPurchaseRequest>('/oa/v1/purchase', input);
}

/** 更新采购申请 */
export function updatePurchase(id: number, input: OaPurchaseInput): Promise<OaPurchaseRequest> {
  return http.put<OaPurchaseRequest>(`/oa/v1/purchase/${id}`, input);
}

/** 删除采购申请 */
export function deletePurchase(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/purchase/${id}`);
}

/** 撤销采购申请 */
export function updatePurchaseStatus(id: number, status: OaAdministrativeStatus): Promise<void> {
  return http.put<void>(`/oa/v1/purchase/${id}/status`, { status });
}

/** 重试推送 ERP 采购订单 */
export function retryPurchasePush(id: number): Promise<OaPurchaseRequest> {
  return http.post<OaPurchaseRequest>(`/oa/v1/purchase/${id}/push-retry`);
}
