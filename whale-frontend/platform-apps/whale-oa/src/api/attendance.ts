// OA 考勤 API 层：打卡记录、日报、配置（考勤组/班次/规则/地点/设备）、补卡与异常
//
// 后端字段：时间字段序列化为 ISO 字符串；日期字段为 YYYY-MM-DD。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 打卡类型 */
export type OaAttendancePunchType = 'IN' | 'OUT';

/** 打卡来源 */
export type OaAttendanceSource = 'APP' | 'DEVICE' | 'WEB';

/** 日报状态 */
export type OaAttendanceDailyStatus =
  'NORMAL' | 'LATE' | 'EARLY' | 'LATE_AND_EARLY' | 'ABSENT' | 'LEAVE' | 'OFFICIAL';

/** 异常类型 */
export type OaAttendanceExceptionType = 'LATE' | 'EARLY' | 'ABSENT';

/** 异常处理状态 */
export type OaAttendanceExceptionStatus = 'PENDING' | 'CONFIRMED' | 'WAIVED';

/** 补卡状态 */
export type OaAttendanceCorrectionStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

/** 打卡记录 */
export interface OaAttendanceRecord {
  id: number;
  userId: number;
  userName?: string;
  punchTime: string;
  punchType: OaAttendancePunchType;
  source?: OaAttendanceSource;
  latitude?: number;
  longitude?: number;
  address?: string;
  deviceCode?: string;
  correction?: number;
  remark?: string;
}

/** 打卡入参 */
export interface OaAttendancePunchInput {
  punchType: OaAttendancePunchType;
  source?: OaAttendanceSource;
  punchTime?: string;
  latitude?: number;
  longitude?: number;
  address?: string;
  deviceCode?: string;
  userName?: string;
  remark?: string;
}

/** 打卡记录查询 */
export interface OaAttendanceRecordQuery {
  current?: number;
  size?: number;
  userId?: number;
  punchType?: OaAttendancePunchType | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 考勤日报 */
export interface OaAttendanceDaily {
  id: number;
  userId: number;
  userName?: string;
  workDate: string;
  groupId?: number;
  shiftCode?: string;
  shiftStart?: string;
  shiftEnd?: string;
  firstIn?: string;
  lastOut?: string;
  lateMinutes?: number;
  earlyMinutes?: number;
  absent?: number;
  status: OaAttendanceDailyStatus;
  remark?: string;
}

/** 日报查询 */
export interface OaAttendanceDailyQuery {
  current?: number;
  size?: number;
  userId?: number;
  status?: OaAttendanceDailyStatus | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 考勤组 */
export interface OaAttendanceGroup {
  id: number;
  code: string;
  name: string;
  shiftId: number;
  ruleId?: number;
  locationId?: number;
  deviceId?: number;
  enabled?: number;
  remark?: string;
  members?: { userId: number; userName?: string }[];
}

/** 考勤组入参 */
export interface OaAttendanceGroupInput {
  code: string;
  name: string;
  shiftId: number;
  ruleId?: number;
  locationId?: number;
  deviceId?: number;
  enabled?: number;
  remark?: string;
  members?: { userId: number; userName?: string }[];
}

/** 班次 */
export interface OaAttendanceShift {
  id: number;
  code: string;
  name: string;
  workStart: string;
  workEnd: string;
  flexMinutes?: number;
  breakMinutes?: number;
  enabled?: number;
  remark?: string;
}

/** 班次入参 */
export interface OaAttendanceShiftInput {
  code: string;
  name: string;
  workStart: string;
  workEnd: string;
  flexMinutes?: number;
  breakMinutes?: number;
  enabled?: number;
  remark?: string;
}

/** 考勤规则 */
export interface OaAttendanceRule {
  id: number;
  code: string;
  name: string;
  allowExternal?: number;
  requireLocation?: number;
  requireDevice?: number;
  maxCorrectionsPerMonth?: number;
  correctionWindowDays?: number;
  enabled?: number;
  remark?: string;
}

/** 考勤规则入参 */
export interface OaAttendanceRuleInput {
  code: string;
  name: string;
  allowExternal?: number;
  requireLocation?: number;
  requireDevice?: number;
  maxCorrectionsPerMonth?: number;
  correctionWindowDays?: number;
  enabled?: number;
  remark?: string;
}

/** 打卡地点 */
export interface OaAttendanceLocation {
  id: number;
  code: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters?: number;
  enabled?: number;
  remark?: string;
}

/** 打卡地点入参 */
export interface OaAttendanceLocationInput {
  code: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters?: number;
  enabled?: number;
  remark?: string;
}

/** 打卡设备 */
export interface OaAttendanceDevice {
  id: number;
  code: string;
  name: string;
  deviceType: string;
  locationId?: number;
  enabled?: number;
  remark?: string;
}

/** 打卡设备入参 */
export interface OaAttendanceDeviceInput {
  code: string;
  name: string;
  deviceType: string;
  locationId?: number;
  enabled?: number;
  remark?: string;
}

/** 补卡申请 */
export interface OaAttendanceCorrection {
  id: number;
  code: string;
  userId: number;
  userName?: string;
  workDate: string;
  punchTime: string;
  punchType: OaAttendancePunchType;
  reason?: string;
  status: OaAttendanceCorrectionStatus;
  approvalInstanceId?: number;
  handler?: string;
  handledTime?: string;
  remark?: string;
}

/** 补卡入参 */
export interface OaAttendanceCorrectionInput {
  workDate: string;
  punchTime: string;
  punchType: OaAttendancePunchType;
  reason?: string;
  userName?: string;
}

/** 补卡查询 */
export interface OaAttendanceCorrectionQuery {
  current?: number;
  size?: number;
  userId?: number;
  status?: OaAttendanceCorrectionStatus | '';
  dateFrom?: string;
  dateTo?: string;
}

/** 考勤异常 */
export interface OaAttendanceException {
  id: number;
  userId: number;
  userName?: string;
  workDate: string;
  type: OaAttendanceExceptionType;
  description?: string;
  status: OaAttendanceExceptionStatus;
  handler?: string;
  handledTime?: string;
  remark?: string;
}

/** 异常查询 */
export interface OaAttendanceExceptionQuery {
  current?: number;
  size?: number;
  userId?: number;
  type?: OaAttendanceExceptionType | '';
  status?: OaAttendanceExceptionStatus | '';
  dateFrom?: string;
  dateTo?: string;
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

/** 分页查询打卡记录 */
export function pageRecords(
  params: OaAttendanceRecordQuery = {},
): Promise<PageResult<OaAttendanceRecord>> {
  return http.get<PageResult<OaAttendanceRecord>>(
    `/oa/v1/attendance/records/page${buildQuery(params)}`,
  );
}

/** 员工打卡 */
export function punch(input: OaAttendancePunchInput): Promise<OaAttendanceRecord> {
  return http.post<OaAttendanceRecord>('/oa/v1/attendance/records', input);
}

/** 分页查询考勤日报 */
export function pageDaily(
  params: OaAttendanceDailyQuery = {},
): Promise<PageResult<OaAttendanceDaily>> {
  return http.get<PageResult<OaAttendanceDaily>>(`/oa/v1/attendance/daily/page${buildQuery(params)}`);
}

/** 按日期区间重算日报 */
export function recalculateDaily(input: {
  dateFrom: string;
  dateTo: string;
  userId?: number;
}): Promise<number> {
  return http.post<number>('/oa/v1/attendance/daily/recalculate', input);
}

/** 查询全部考勤组 */
export function listGroups(): Promise<OaAttendanceGroup[]> {
  return http.get<OaAttendanceGroup[]>('/oa/v1/attendance/groups');
}

/** 新增考勤组 */
export function createGroup(input: OaAttendanceGroupInput): Promise<OaAttendanceGroup> {
  return http.post<OaAttendanceGroup>('/oa/v1/attendance/groups', input);
}

/** 更新考勤组 */
export function updateGroup(id: number, input: OaAttendanceGroupInput): Promise<OaAttendanceGroup> {
  return http.put<OaAttendanceGroup>(`/oa/v1/attendance/groups/${id}`, input);
}

/** 删除考勤组 */
export function deleteGroup(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/attendance/groups/${id}`);
}

/** 查询全部班次 */
export function listShifts(): Promise<OaAttendanceShift[]> {
  return http.get<OaAttendanceShift[]>('/oa/v1/attendance/shifts');
}

/** 新增班次 */
export function createShift(input: OaAttendanceShiftInput): Promise<OaAttendanceShift> {
  return http.post<OaAttendanceShift>('/oa/v1/attendance/shifts', input);
}

/** 更新班次 */
export function updateShift(id: number, input: OaAttendanceShiftInput): Promise<OaAttendanceShift> {
  return http.put<OaAttendanceShift>(`/oa/v1/attendance/shifts/${id}`, input);
}

/** 删除班次 */
export function deleteShift(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/attendance/shifts/${id}`);
}

/** 查询全部考勤规则 */
export function listRules(): Promise<OaAttendanceRule[]> {
  return http.get<OaAttendanceRule[]>('/oa/v1/attendance/rules');
}

/** 新增考勤规则 */
export function createRule(input: OaAttendanceRuleInput): Promise<OaAttendanceRule> {
  return http.post<OaAttendanceRule>('/oa/v1/attendance/rules', input);
}

/** 更新考勤规则 */
export function updateRule(id: number, input: OaAttendanceRuleInput): Promise<OaAttendanceRule> {
  return http.put<OaAttendanceRule>(`/oa/v1/attendance/rules/${id}`, input);
}

/** 删除考勤规则 */
export function deleteRule(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/attendance/rules/${id}`);
}

/** 查询全部打卡地点 */
export function listLocations(): Promise<OaAttendanceLocation[]> {
  return http.get<OaAttendanceLocation[]>('/oa/v1/attendance/locations');
}

/** 新增打卡地点 */
export function createLocation(input: OaAttendanceLocationInput): Promise<OaAttendanceLocation> {
  return http.post<OaAttendanceLocation>('/oa/v1/attendance/locations', input);
}

/** 更新打卡地点 */
export function updateLocation(
  id: number,
  input: OaAttendanceLocationInput,
): Promise<OaAttendanceLocation> {
  return http.put<OaAttendanceLocation>(`/oa/v1/attendance/locations/${id}`, input);
}

/** 删除打卡地点 */
export function deleteLocation(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/attendance/locations/${id}`);
}

/** 查询全部打卡设备 */
export function listDevices(): Promise<OaAttendanceDevice[]> {
  return http.get<OaAttendanceDevice[]>('/oa/v1/attendance/devices');
}

/** 新增打卡设备 */
export function createDevice(input: OaAttendanceDeviceInput): Promise<OaAttendanceDevice> {
  return http.post<OaAttendanceDevice>('/oa/v1/attendance/devices', input);
}

/** 更新打卡设备 */
export function updateDevice(
  id: number,
  input: OaAttendanceDeviceInput,
): Promise<OaAttendanceDevice> {
  return http.put<OaAttendanceDevice>(`/oa/v1/attendance/devices/${id}`, input);
}

/** 删除打卡设备 */
export function deleteDevice(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/attendance/devices/${id}`);
}

/** 分页查询考勤异常 */
export function pageExceptions(
  params: OaAttendanceExceptionQuery = {},
): Promise<PageResult<OaAttendanceException>> {
  return http.get<PageResult<OaAttendanceException>>(
    `/oa/v1/attendance/exceptions/page${buildQuery(params)}`,
  );
}

/** 处理考勤异常（确认或豁免） */
export function handleException(id: number, status: string, remark?: string): Promise<void> {
  return http.put<void>(`/oa/v1/attendance/exceptions/${id}/status`, { status, remark });
}

/** 分页查询补卡申请 */
export function pageCorrections(
  params: OaAttendanceCorrectionQuery = {},
): Promise<PageResult<OaAttendanceCorrection>> {
  return http.get<PageResult<OaAttendanceCorrection>>(
    `/oa/v1/attendance/corrections/page${buildQuery(params)}`,
  );
}

/** 提交补卡申请 */
export function createCorrection(
  input: OaAttendanceCorrectionInput,
): Promise<OaAttendanceCorrection> {
  return http.post<OaAttendanceCorrection>('/oa/v1/attendance/corrections', input);
}

/** 审批 / 撤销补卡申请 */
export function updateCorrectionStatus(id: number, status: string, remark?: string): Promise<void> {
  return http.put<void>(`/oa/v1/attendance/corrections/${id}/status`, { status, remark });
}
