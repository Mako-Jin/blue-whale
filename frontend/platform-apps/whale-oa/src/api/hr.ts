// OA 人事模块 API 层：员工档案、假期类型、额度台账与工作日历
//
// 字段对齐 backend whale-oa hr 包下的 view/command；雪花 id 为 number，日期为 YYYY-MM-DD 字符串。

import { http, type PageResult } from './client';

/** 在职状态 */
export type EmploymentStatus = 'ACTIVE' | 'PROBATION' | 'REGULAR' | 'LEFT';

/** 假期计量单位 */
export type LeaveTypeUnit = 'DAY' | 'HOUR';

/** 额度流水动作 */
export type LeaveBalanceAction = 'INIT' | 'ADJUST' | 'FREEZE' | 'RELEASE' | 'DEDUCT';

/** 员工扩展档案（对齐 EmployeeProfileView） */
export interface EmployeeProfile {
  id: number;
  userId: number;
  employeeNo?: string;
  deptId?: number;
  hireDate?: string;
  regularDate?: string;
  birthday?: string;
  employmentStatus?: EmploymentStatus;
  leaveDate?: string;
  cultureVisible?: boolean;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 员工档案保存命令（对齐 EmployeeProfileSaveCommand，按 userId 幂等 upsert） */
export interface EmployeeProfileSaveCommand {
  userId: number;
  employeeNo?: string;
  deptId?: number;
  hireDate?: string;
  regularDate?: string;
  birthday?: string;
  employmentStatus?: EmploymentStatus;
  leaveDate?: string;
  cultureVisible?: boolean;
  remark?: string;
}

/** 员工档案查询入参 */
export interface EmployeeProfileQuery {
  current?: number;
  size?: number;
  userId?: number;
  employeeNo?: string;
  deptId?: number;
  employmentStatus?: EmploymentStatus | '';
}

/** 假期类型（对齐 LeaveTypeView） */
export interface LeaveTypeView {
  id: number;
  code: string;
  name: string;
  unit: LeaveTypeUnit;
  annualQuota: number;
  minUnit: number;
  requiresAttachment?: boolean;
  allowNegative?: boolean;
  enabled?: boolean;
  sortNo?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 假期类型保存命令（对齐 LeaveTypeSaveCommand） */
export interface LeaveTypeSaveCommand {
  code: string;
  name: string;
  unit: LeaveTypeUnit;
  annualQuota: number;
  minUnit: number;
  requiresAttachment?: boolean;
  allowNegative?: boolean;
  enabled?: boolean;
  sortNo?: number;
  remark?: string;
}

/** 假期类型查询入参 */
export interface LeaveTypeQuery {
  current?: number;
  size?: number;
  code?: string;
  name?: string;
  enabled?: boolean;
}

/** 年度假期额度（对齐 LeaveBalanceView） */
export interface LeaveBalanceView {
  id: number;
  userId: number;
  leaveTypeId: number;
  balanceYear: number;
  totalDays: number;
  usedDays: number;
  frozenDays: number;
  availableDays: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 假期额度流水（对齐 LeaveBalanceLogView） */
export interface LeaveBalanceLogView {
  id: number;
  balanceId: number;
  userId: number;
  leaveTypeId: number;
  balanceYear: number;
  action: LeaveBalanceAction;
  days: number;
  beforeTotal: number;
  afterTotal: number;
  beforeUsed: number;
  afterUsed: number;
  beforeFrozen: number;
  afterFrozen: number;
  bizType?: string;
  bizId?: number;
  operatorId?: number;
  remark?: string;
  createTime?: string;
}

/** 假期额度查询入参（对齐 LeaveBalanceQuery） */
export interface LeaveBalanceQuery {
  current?: number;
  size?: number;
  userId?: number;
  leaveTypeId?: number;
  balanceYear?: number;
}

/** 额度流水查询入参（对齐 LeaveBalanceLogQuery） */
export interface LeaveBalanceLogQuery {
  current?: number;
  size?: number;
  balanceId?: number;
  userId?: number;
  leaveTypeId?: number;
  balanceYear?: number;
  action?: LeaveBalanceAction;
}

/** 额度初始化命令（对齐 LeaveBalanceInitCommand） */
export interface LeaveBalanceInitCommand {
  userId: number;
  year: number;
}

/** 额度调整命令（对齐 LeaveBalanceAdjustCommand） */
export interface LeaveBalanceAdjustCommand {
  totalDays: number;
  remark?: string;
}

/** 工作日历（对齐 WorkCalendarView，主键为 calendarDate） */
export interface WorkCalendarView {
  calendarDate: string;
  workday: boolean;
  workHours?: number;
  holidayName?: string;
  remark?: string;
}

/** 工作日历保存命令（对齐 WorkCalendarSaveCommand，按日期幂等 upsert） */
export interface WorkCalendarSaveCommand {
  workday: boolean;
  workHours?: number;
  holidayName?: string;
  remark?: string;
}

/** 工作日历查询入参 */
export interface WorkCalendarQuery {
  current?: number;
  size?: number;
  yearMonth?: string;
  startDate?: string;
  endDate?: string;
  workday?: boolean;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询员工档案 */
export function pageProfiles(
  params: EmployeeProfileQuery = {},
): Promise<PageResult<EmployeeProfile>> {
  return http.get<PageResult<EmployeeProfile>>(`/oa/hr/profile/page${buildQuery(params)}`);
}

/** 按用户ID查询员工档案 */
export function getProfile(userId: number): Promise<EmployeeProfile> {
  return http.get<EmployeeProfile>(`/oa/hr/profile/${userId}`);
}

/** 保存员工档案（按 userId 幂等 upsert） */
export function saveProfile(input: EmployeeProfileSaveCommand): Promise<EmployeeProfile> {
  return http.post<EmployeeProfile>('/oa/hr/profile', input);
}

/** 删除员工档案 */
export function deleteProfile(userId: number): Promise<void> {
  return http.del<void>(`/oa/hr/profile/${userId}`);
}

/** 分页查询假期类型 */
export function pageLeaveTypes(params: LeaveTypeQuery = {}): Promise<PageResult<LeaveTypeView>> {
  return http.get<PageResult<LeaveTypeView>>(`/oa/hr/leave-type/page${buildQuery(params)}`);
}

/** 查询全部启用假期类型 */
export function listEnabledLeaveTypes(): Promise<LeaveTypeView[]> {
  return http.get<LeaveTypeView[]>('/oa/hr/leave-type/enabled');
}

/** 新增假期类型 */
export function createLeaveType(input: LeaveTypeSaveCommand): Promise<LeaveTypeView> {
  return http.post<LeaveTypeView>('/oa/hr/leave-type', input);
}

/** 更新假期类型 */
export function updateLeaveType(id: number, input: LeaveTypeSaveCommand): Promise<LeaveTypeView> {
  return http.put<LeaveTypeView>(`/oa/hr/leave-type/${id}`, input);
}

/** 启用 / 停用假期类型 */
export function toggleLeaveType(id: number, enabled: boolean): Promise<LeaveTypeView> {
  return http.put<LeaveTypeView>(`/oa/hr/leave-type/${id}/toggle${buildQuery({ enabled })}`);
}

/** 分页查询年度假期额度 */
export function pageLeaveBalances(
  params: LeaveBalanceQuery = {},
): Promise<PageResult<LeaveBalanceView>> {
  return http.get<PageResult<LeaveBalanceView>>(`/oa/hr/leave-balance/page${buildQuery(params)}`);
}

/** 查询当前用户某年度额度 */
export function myLeaveBalances(year: number): Promise<LeaveBalanceView[]> {
  return http.get<LeaveBalanceView[]>(`/oa/hr/leave-balance/my${buildQuery({ year })}`);
}

/** 分页查询额度流水 */
export function pageLeaveBalanceLogs(
  id: number,
  params: LeaveBalanceLogQuery = {},
): Promise<PageResult<LeaveBalanceLogView>> {
  return http.get<PageResult<LeaveBalanceLogView>>(
    `/oa/hr/leave-balance/${id}/logs${buildQuery(params)}`,
  );
}

/** 初始化某用户某年度额度（幂等） */
export function initLeaveBalance(input: LeaveBalanceInitCommand): Promise<LeaveBalanceView[]> {
  return http.post<LeaveBalanceView[]>('/oa/hr/leave-balance/init', input);
}

/** 调整额度总额度 */
export function adjustLeaveBalance(
  id: number,
  input: LeaveBalanceAdjustCommand,
): Promise<LeaveBalanceView> {
  return http.put<LeaveBalanceView>(`/oa/hr/leave-balance/${id}`, input);
}

/** 分页查询工作日历 */
export function pageWorkCalendars(
  params: WorkCalendarQuery = {},
): Promise<PageResult<WorkCalendarView>> {
  return http.get<PageResult<WorkCalendarView>>(`/oa/hr/calendar/page${buildQuery(params)}`);
}

/** 按日期保存工作日历（幂等 upsert） */
export function saveWorkCalendar(
  date: string,
  input: WorkCalendarSaveCommand,
): Promise<WorkCalendarView> {
  return http.put<WorkCalendarView>(`/oa/hr/calendar/${date}`, input);
}
