// ERP 轻量人力 API：组织、员工档案与异动、考勤汇总、薪资批次与工资条、绩效
import { http } from './client';
import type { PageResult } from './masterdata';

/** 人力查询参数 */
export interface HrQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  period?: string;
  orgCode?: string;
  employeeNo?: string;
}

/** 组织单元 */
export interface OrgUnitView {
  id: number;
  code: string;
  name: string;
  parentCode?: string;
  manager?: string;
  status: string;
  remark?: string;
}

/** 组织单元入参 */
export interface OrgUnitInput {
  code: string;
  name: string;
  parentCode?: string;
  manager?: string;
  remark?: string;
}

/** 员工档案 */
export interface EmployeeView {
  id: number;
  employeeNo: string;
  name: string;
  gender?: string;
  phone?: string;
  email?: string;
  orgCode: string;
  position?: string;
  hireDate: string;
  leaveDate?: string;
  status: string;
  baseSalary: number;
  allowance: number;
  socialBase: number;
  userId?: number;
  remark?: string;
}

/** 员工建档入参 */
export interface EmployeeCreateInput {
  employeeNo: string;
  name: string;
  gender?: string;
  phone?: string;
  email?: string;
  orgCode: string;
  position?: string;
  hireDate: string;
  baseSalary?: number;
  allowance?: number;
  socialBase?: number;
  userId?: number;
  remark?: string;
}

/** 员工档案编辑入参 */
export interface EmployeeUpdateInput {
  name: string;
  gender?: string;
  phone?: string;
  email?: string;
  position?: string;
  baseSalary?: number;
  allowance?: number;
  socialBase?: number;
  userId?: number;
  remark?: string;
}

/** 员工异动单 */
export interface EmploymentChangeView {
  id: number;
  changeNo: string;
  employeeNo: string;
  changeType: string;
  effectiveDate: string;
  fromOrgCode?: string;
  toOrgCode?: string;
  fromPosition?: string;
  toPosition?: string;
  fromSalary?: number;
  toSalary?: number;
  status: string;
  remark?: string;
}

/** 员工异动入参 */
export interface EmploymentChangeInput {
  employeeNo: string;
  changeType: string;
  effectiveDate: string;
  toOrgCode?: string;
  toPosition?: string;
  toSalary?: number;
  remark?: string;
}

/** 考勤汇总 */
export interface AttendanceView {
  id: number;
  employeeNo: string;
  period: string;
  workDays: number;
  actualDays: number;
  leaveHours: number;
  overtimeHours: number;
  absentDays: number;
  remark?: string;
}

/** 考勤登记入参 */
export interface AttendanceInput {
  employeeNo: string;
  period: string;
  workDays?: number;
  actualDays?: number;
  leaveHours?: number;
  overtimeHours?: number;
  absentDays?: number;
  remark?: string;
}

/** 工资条 */
export interface PayslipView {
  id: number;
  payrollId: number;
  employeeNo: string;
  employeeName?: string;
  orgCode?: string;
  baseSalary: number;
  allowance: number;
  overtimePay: number;
  leaveDeduction: number;
  absenceDeduction: number;
  grossPay: number;
  socialInsurance: number;
  housingFund: number;
  tax: number;
  netPay: number;
}

/** 薪资批次 */
export interface PayrollRunView {
  id: number;
  runNo: string;
  period: string;
  status: string;
  employeeCount: number;
  totalGross: number;
  totalSocialInsurance: number;
  totalHousingFund: number;
  totalTax: number;
  totalNet: number;
  remark?: string;
  payslips?: PayslipView[];
}

/** 绩效指标 */
export interface KpiView {
  id: number;
  name: string;
  weight: number;
  targetValue: number;
  unit?: string;
  remark?: string;
}

/** 考核结果 */
export interface AppraisalView {
  id: number;
  kpiId: number;
  kpiName?: string;
  employeeNo: string;
  employeeName?: string;
  score: number;
  grade: string;
  remark?: string;
}

/** 绩效周期 */
export interface PerformanceCycleView {
  id: number;
  cycleNo: string;
  name: string;
  period: string;
  startDate: string;
  endDate: string;
  status: string;
  remark?: string;
  kpis?: KpiView[];
  appraisals?: AppraisalView[];
}

/** 绩效周期入参 */
export interface PerformanceCycleInput {
  name: string;
  period: string;
  startDate: string;
  endDate: string;
  remark?: string;
}

/** 指标入参 */
export interface KpiInput {
  name: string;
  weight: number;
  targetValue: number;
  unit?: string;
  remark?: string;
}

/** 考核入参 */
export interface AppraisalInput {
  employeeNo: string;
  kpiId: number;
  score: number;
  remark?: string;
}

/** 人力状态中文标签 */
export const HR_STATUS_LABEL: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
  ACTIVE: '在职',
  LEFT: '离职',
  DRAFT: '草稿',
  EFFECTIVE: '已生效',
  CANCELLED: '已取消',
  CALCULATED: '已计算',
  APPROVED: '已审批',
  PAID: '已发放',
  CLOSED: '已结账',
  IN_PROGRESS: '进行中',
};

/** 异动类型中文标签 */
export const CHANGE_TYPE_LABEL: Record<string, string> = {
  HIRE: '入职',
  TRANSFER: '调动',
  PROMOTION: '晋升',
  RESIGN: '离职',
  RETURN: '复职',
};

/** 状态标签颜色 */
export function hrStatusColor(status: string): string {
  if (['PAID', 'CLOSED', 'ACTIVE', 'ENABLED', 'EFFECTIVE'].includes(status)) return 'green';
  if (['APPROVED', 'CALCULATED', 'IN_PROGRESS'].includes(status)) return 'blue';
  if (['CANCELLED', 'LEFT', 'DISABLED'].includes(status)) return 'default';
  if (status === 'DRAFT') return 'orange';
  return 'default';
}

/** 构造查询串（空值不下发，默认每页 20 条） */
function buildQuery(params: HrQueryParams): string {
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

/** 组织列表 */
export function listHrOrgs(params: HrQueryParams = {}): Promise<OrgUnitView[]> {
  return http.get<OrgUnitView[]>(`/erp/hr/org?${buildQuery(params)}`);
}

/** 新增组织 */
export function createHrOrg(input: OrgUnitInput): Promise<OrgUnitView> {
  return http.post<OrgUnitView>('/erp/hr/org', input);
}

/** 编辑组织 */
export function updateHrOrg(code: string, input: OrgUnitInput): Promise<OrgUnitView> {
  return http.put<OrgUnitView>(`/erp/hr/org/${code}`, input);
}

/** 员工分页 */
export function pageEmployees(params: HrQueryParams = {}): Promise<PageResult<EmployeeView>> {
  return http.get<PageResult<EmployeeView>>(`/erp/hr/employee/page?${buildQuery(params)}`);
}

/** 员工详情 */
export function getEmployee(employeeNo: string): Promise<EmployeeView> {
  return http.get<EmployeeView>(`/erp/hr/employee/${employeeNo}`);
}

/** 新增员工 */
export function createEmployee(input: EmployeeCreateInput): Promise<EmployeeView> {
  return http.post<EmployeeView>('/erp/hr/employee', input);
}

/** 编辑员工 */
export function updateEmployee(
  employeeNo: string,
  input: EmployeeUpdateInput,
): Promise<EmployeeView> {
  return http.put<EmployeeView>(`/erp/hr/employee/${employeeNo}`, input);
}

/** 异动单分页 */
export function pageEmploymentChanges(
  params: HrQueryParams = {},
): Promise<PageResult<EmploymentChangeView>> {
  return http.get<PageResult<EmploymentChangeView>>(
    `/erp/hr/employee/change/page?${buildQuery(params)}`,
  );
}

/** 新增异动单 */
export function createEmploymentChange(
  input: EmploymentChangeInput,
): Promise<EmploymentChangeView> {
  return http.post<EmploymentChangeView>('/erp/hr/employee/change', input);
}

/** 异动单生效 */
export function effectiveEmploymentChange(id: number): Promise<EmploymentChangeView> {
  return http.post<EmploymentChangeView>(`/erp/hr/employee/change/${id}/effective`);
}

/** 考勤分页 */
export function pageAttendances(params: HrQueryParams = {}): Promise<PageResult<AttendanceView>> {
  return http.get<PageResult<AttendanceView>>(`/erp/hr/attendance/page?${buildQuery(params)}`);
}

/** 登记考勤（按员工 + 期间 upsert） */
export function saveAttendance(input: AttendanceInput): Promise<AttendanceView> {
  return http.post<AttendanceView>('/erp/hr/attendance', input);
}

/** 薪资批次分页 */
export function pagePayrolls(params: HrQueryParams = {}): Promise<PageResult<PayrollRunView>> {
  return http.get<PageResult<PayrollRunView>>(`/erp/hr/payroll/page?${buildQuery(params)}`);
}

/** 薪资批次详情（含工资条） */
export function getPayroll(id: number): Promise<PayrollRunView> {
  return http.get<PayrollRunView>(`/erp/hr/payroll/${id}`);
}

/** 新建薪资批次 */
export function createPayroll(input: { period: string; remark?: string }): Promise<PayrollRunView> {
  return http.post<PayrollRunView>('/erp/hr/payroll', input);
}

/** 计算薪资 */
export function calculatePayroll(id: number): Promise<PayrollRunView> {
  return http.post<PayrollRunView>(`/erp/hr/payroll/${id}/calculate`);
}

/** 审批薪资（生成计提凭证） */
export function approvePayroll(id: number): Promise<PayrollRunView> {
  return http.post<PayrollRunView>(`/erp/hr/payroll/${id}/approve`);
}

/** 发放薪资（生成发放凭证） */
export function payPayroll(id: number): Promise<PayrollRunView> {
  return http.post<PayrollRunView>(`/erp/hr/payroll/${id}/pay`);
}

/** 薪资批次结账 */
export function closePayroll(id: number): Promise<PayrollRunView> {
  return http.post<PayrollRunView>(`/erp/hr/payroll/${id}/close`);
}

/** 取消薪资批次 */
export function cancelPayroll(id: number): Promise<PayrollRunView> {
  return http.post<PayrollRunView>(`/erp/hr/payroll/${id}/cancel`);
}

/** 我的工资条（仅本人） */
export function myPayslips(params: HrQueryParams = {}): Promise<PageResult<PayrollRunView>> {
  return http.get<PageResult<PayrollRunView>>(`/erp/hr/payroll/payslip/my?${buildQuery(params)}`);
}

/** 绩效周期分页 */
export function pagePerformanceCycles(
  params: HrQueryParams = {},
): Promise<PageResult<PerformanceCycleView>> {
  return http.get<PageResult<PerformanceCycleView>>(
    `/erp/hr/performance/page?${buildQuery(params)}`,
  );
}

/** 绩效周期详情（含指标与考核） */
export function getPerformanceCycle(id: number): Promise<PerformanceCycleView> {
  return http.get<PerformanceCycleView>(`/erp/hr/performance/${id}`);
}

/** 新建绩效周期 */
export function createPerformanceCycle(
  input: PerformanceCycleInput,
): Promise<PerformanceCycleView> {
  return http.post<PerformanceCycleView>('/erp/hr/performance', input);
}

/** 新增绩效指标 */
export function addKpi(id: number, input: KpiInput): Promise<PerformanceCycleView> {
  return http.post<PerformanceCycleView>(`/erp/hr/performance/${id}/kpi`, input);
}

/** 录入考核结果 */
export function appraise(
  id: number,
  input: AppraisalInput,
): Promise<PerformanceCycleView> {
  return http.post<PerformanceCycleView>(`/erp/hr/performance/${id}/appraise`, input);
}

/** 关闭绩效周期 */
export function closePerformanceCycle(id: number): Promise<PerformanceCycleView> {
  return http.post<PerformanceCycleView>(`/erp/hr/performance/${id}/close`);
}
