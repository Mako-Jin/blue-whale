// OA 人事模块 API 层：员工档案、假期类型、额度台账与工作日历
//
// 字段对齐 backend whale-oa hr 包下的 view/command；雪花 id 为 number，日期为 YYYY-MM-DD 字符串。

import { http, type PageResult } from './client';

/** 在职状态 */
export type EmploymentStatus = 'ACTIVE' | 'PROBATION' | 'REGULAR' | 'LEFT';

/** 假期计量单位 */
export type LeaveTypeUnit = 'DAY' | 'HOUR';

/** 额度流水动作 */
export type LeaveBalanceAction = 'INIT' | 'ADJUST' | 'FREEZE' | 'RELEASE' | 'DEDUCT' | 'GRANT';

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
  return http.get<PageResult<EmployeeProfile>>(`/oa/v1/hr/profile/page${buildQuery(params)}`);
}

/** 按用户ID查询员工档案 */
export function getProfile(userId: number): Promise<EmployeeProfile> {
  return http.get<EmployeeProfile>(`/oa/v1/hr/profile/${userId}`);
}

/** 保存员工档案（按 userId 幂等 upsert） */
export function saveProfile(input: EmployeeProfileSaveCommand): Promise<EmployeeProfile> {
  return http.post<EmployeeProfile>('/oa/v1/hr/profile', input);
}

/** 删除员工档案 */
export function deleteProfile(userId: number): Promise<void> {
  return http.del<void>(`/oa/v1/hr/profile/${userId}`);
}

/** 分页查询假期类型 */
export function pageLeaveTypes(params: LeaveTypeQuery = {}): Promise<PageResult<LeaveTypeView>> {
  return http.get<PageResult<LeaveTypeView>>(`/oa/v1/hr/leave-type/page${buildQuery(params)}`);
}

/** 查询全部启用假期类型 */
export function listEnabledLeaveTypes(): Promise<LeaveTypeView[]> {
  return http.get<LeaveTypeView[]>('/oa/v1/hr/leave-type/enabled');
}

/** 新增假期类型 */
export function createLeaveType(input: LeaveTypeSaveCommand): Promise<LeaveTypeView> {
  return http.post<LeaveTypeView>('/oa/v1/hr/leave-type', input);
}

/** 更新假期类型 */
export function updateLeaveType(id: number, input: LeaveTypeSaveCommand): Promise<LeaveTypeView> {
  return http.put<LeaveTypeView>(`/oa/v1/hr/leave-type/${id}`, input);
}

/** 启用 / 停用假期类型 */
export function toggleLeaveType(id: number, enabled: boolean): Promise<LeaveTypeView> {
  return http.put<LeaveTypeView>(`/oa/v1/hr/leave-type/${id}/toggle${buildQuery({ enabled })}`);
}

/** 分页查询年度假期额度 */
export function pageLeaveBalances(
  params: LeaveBalanceQuery = {},
): Promise<PageResult<LeaveBalanceView>> {
  return http.get<PageResult<LeaveBalanceView>>(
    `/oa/v1/hr/leave-balance/page${buildQuery(params)}`,
  );
}

/** 查询当前用户某年度额度 */
export function myLeaveBalances(year: number): Promise<LeaveBalanceView[]> {
  return http.get<LeaveBalanceView[]>(`/oa/v1/hr/leave-balance/my${buildQuery({ year })}`);
}

/** 分页查询额度流水 */
export function pageLeaveBalanceLogs(
  id: number,
  params: LeaveBalanceLogQuery = {},
): Promise<PageResult<LeaveBalanceLogView>> {
  return http.get<PageResult<LeaveBalanceLogView>>(
    `/oa/v1/hr/leave-balance/${id}/logs${buildQuery(params)}`,
  );
}

/** 初始化某用户某年度额度（幂等） */
export function initLeaveBalance(input: LeaveBalanceInitCommand): Promise<LeaveBalanceView[]> {
  return http.post<LeaveBalanceView[]>('/oa/v1/hr/leave-balance/init', input);
}

/** 调整额度总额度 */
export function adjustLeaveBalance(
  id: number,
  input: LeaveBalanceAdjustCommand,
): Promise<LeaveBalanceView> {
  return http.put<LeaveBalanceView>(`/oa/v1/hr/leave-balance/${id}`, input);
}

/** 分页查询工作日历 */
export function pageWorkCalendars(
  params: WorkCalendarQuery = {},
): Promise<PageResult<WorkCalendarView>> {
  return http.get<PageResult<WorkCalendarView>>(`/oa/v1/hr/calendar/page${buildQuery(params)}`);
}

/** 按日期保存工作日历（幂等 upsert） */
export function saveWorkCalendar(
  date: string,
  input: WorkCalendarSaveCommand,
): Promise<WorkCalendarView> {
  return http.put<WorkCalendarView>(`/oa/v1/hr/calendar/${date}`, input);
}

/** 人员异动类型 */
export type EmployeeChangeType = 'ONBOARD' | 'REGULAR' | 'TRANSFER' | 'LEAVE' | 'REHIRE';

/** 人员异动状态 */
export type EmployeeChangeStatus =
  'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'EXEC_FAILED';

/** 离职清单任务状态 */
export type EmployeeChangeTaskStatus = 'PENDING' | 'DONE';

/** 人员异动任务（对齐 EmployeeChangeViews.Task） */
export interface EmployeeChangeTask {
  id: number;
  taskCode: string;
  taskName: string;
  assigneeId?: number;
  assigneeName?: string;
  status?: EmployeeChangeTaskStatus;
  doneTime?: string;
  remark?: string;
}

/** 人员异动列表项（对齐 EmployeeChangeViews.ListItem） */
export interface EmployeeChangeListItem {
  id: number;
  changeNo: string;
  changeType: EmployeeChangeType;
  changeTypeLabel?: string;
  userId?: number;
  employeeName: string;
  effectiveDate?: string;
  status: EmployeeChangeStatus;
  applicantName?: string;
  executedTime?: string;
  createTime?: string;
}

/** 人员异动详情（对齐 EmployeeChangeViews.Detail） */
export interface EmployeeChangeDetail {
  id: number;
  changeNo: string;
  changeType: EmployeeChangeType;
  changeTypeLabel?: string;
  userId?: number;
  employeeName: string;
  username?: string;
  fromDeptId?: number;
  toDeptId?: number;
  fromStatus?: EmploymentStatus;
  toStatus?: EmploymentStatus;
  effectiveDate?: string;
  reason?: string;
  status: EmployeeChangeStatus;
  approvalInstanceId?: number;
  applicantId?: number;
  applicantName?: string;
  executorId?: number;
  executorName?: string;
  executedTime?: string;
  executeResult?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  tasks?: EmployeeChangeTask[];
}

/** 人员异动保存命令（对齐 EmployeeChangeCommands.Save） */
export interface EmployeeChangeSaveCommand {
  changeType: EmployeeChangeType;
  userId?: number;
  employeeName: string;
  username?: string;
  fromDeptId?: number;
  toDeptId?: number;
  fromStatus?: EmploymentStatus;
  toStatus?: EmploymentStatus;
  effectiveDate: string;
  reason?: string;
  remark?: string;
}

/** 人员异动查询入参 */
export interface EmployeeChangeQuery {
  current?: number;
  size?: number;
  keyword?: string;
  changeType?: EmployeeChangeType | '';
  status?: EmployeeChangeStatus | '';
  userId?: number;
}

/** 人员异动执行结果（对齐 EmployeeChangeViews.ExecuteResult） */
export interface EmployeeChangeExecuteResult {
  id: number;
  status: EmployeeChangeStatus;
  message?: string;
  temporaryPassword?: string;
}

/** 分页查询人员异动单 */
export function pageChanges(
  params: EmployeeChangeQuery = {},
): Promise<PageResult<EmployeeChangeListItem>> {
  return http.get<PageResult<EmployeeChangeListItem>>(`/oa/v1/hr/change/page${buildQuery(params)}`);
}

/** 查询人员异动单详情 */
export function getChange(id: number): Promise<EmployeeChangeDetail> {
  return http.get<EmployeeChangeDetail>(`/oa/v1/hr/change/${id}`);
}

/** 新建人员异动单 */
export function createChange(input: EmployeeChangeSaveCommand): Promise<EmployeeChangeDetail> {
  return http.post<EmployeeChangeDetail>('/oa/v1/hr/change', input);
}

/** 编辑人员异动单 */
export function updateChange(
  id: number,
  input: EmployeeChangeSaveCommand,
): Promise<EmployeeChangeDetail> {
  return http.put<EmployeeChangeDetail>(`/oa/v1/hr/change/${id}`, input);
}

/** 提交人员异动审批 */
export function submitChange(id: number): Promise<EmployeeChangeDetail> {
  return http.post<EmployeeChangeDetail>(`/oa/v1/hr/change/${id}/submit`);
}

/** 执行审批通过的人员异动 */
export function executeChange(id: number): Promise<EmployeeChangeExecuteResult> {
  return http.post<EmployeeChangeExecuteResult>(`/oa/v1/hr/change/${id}/execute`);
}

/** 完成离职清单任务 */
export function doneChangeTask(changeId: number, taskId: number, remark?: string): Promise<void> {
  return http.put<void>(`/oa/v1/hr/change/${changeId}/tasks/${taskId}/done`, { remark });
}
