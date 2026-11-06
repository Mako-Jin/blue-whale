// LMS 培训域接口封装（M2：培训班 / 场次 / 必修分配 / 签到 / 讲师课时）。
//
// 后端：whale-lms 9008，网关自持 /lms 一级前缀（REQ-ARCH-003）。

import { http } from './client';

/** 培训班 */
export interface TrainingClassItem {
  id: number;
  classCode: string;
  title: string;
  courseId?: number;
  mode: 'ONLINE' | 'OFFLINE' | 'BLENDED';
  lecturer?: string;
  startDate: string;
  endDate: string;
  location?: string;
  capacity?: number;
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED';
  remark?: string;
  assignedCount?: number;
  sessionCount?: number;
  createTime?: string;
}

/** 场次 */
export interface SessionItem {
  id: number;
  sessionNo: number;
  title: string;
  startTime: string;
  endTime: string;
  location?: string;
  lecturer?: string;
  hours: number;
}

/** 培训班详情 */
export interface TrainingClassDetail extends TrainingClassItem {
  sessions: SessionItem[];
  checkinCount?: number;
  totalHours?: number;
}

/** 签到记录 */
export interface CheckinItem {
  id: number;
  classId: number;
  sessionId: number;
  userId: number;
  username?: string;
  checkinType: 'SELF' | 'SUPPLEMENT';
  checkinAt: string;
  operatorId?: number;
  remark?: string;
}

/** 讲师课时 */
export interface LecturerHoursItem {
  lecturer: string;
  sessionCount: number;
  totalHours: number;
}

/** 分配结果 */
export interface AssignmentResult {
  created: number;
  skipped: number;
}

/** 完成认定明细：basis 说明认定依据（签到 / 课程进度 / 未达成原因） */
export interface CompletionItem {
  userId: number;
  username?: string;
  assignedAt?: string;
  sessionsTotal: number;
  sessionsAttended: number;
  coursePercent?: number;
  completed: boolean;
  basis: string;
}

/** 完成认定汇总 */
export interface TrainingCompletion {
  classId: number;
  total: number;
  completed: number;
  completionRate: number;
  items: CompletionItem[];
}

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 培训班保存载荷 */
export interface TrainingClassPayload {
  classCode: string;
  title: string;
  courseId?: number;
  mode?: string;
  lecturer?: string;
  startDate: string;
  endDate: string;
  location?: string;
  capacity?: number;
  remark?: string;
}

/** 培训班分页 */
export function pageTrainingClasses(params: {
  keyword?: string;
  mode?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<TrainingClassItem>>('/lms/training/page', { ...params });
}

/** 培训班详情 */
export function trainingClassDetail(id: number | string) {
  return http.get<TrainingClassDetail>(`/lms/training/${id}`);
}

/** 新建培训班 */
export function createTrainingClass(body: TrainingClassPayload) {
  return http.post<number>('/lms/training', body);
}

/** 编辑培训班（仅草稿） */
export function updateTrainingClass(id: number, body: TrainingClassPayload) {
  return http.put<void>(`/lms/training/${id}`, body);
}

/** 发布 / 结课 */
export function changeTrainingClassStatus(id: number, action: 'PUBLISH' | 'CLOSE') {
  return http.put<void>(`/lms/training/${id}/status`, { action });
}

/** 删除培训班（仅草稿） */
export function deleteTrainingClass(id: number) {
  return http.del<void>(`/lms/training/${id}`);
}

/** 新增场次（仅草稿） */
export function addSession(
  classId: number,
  body: { sessionNo?: number; title: string; startTime: string; endTime: string; location?: string; lecturer?: string; hours: number },
) {
  return http.post<number>(`/lms/training/${classId}/session`, body);
}

/** 删除场次（仅草稿） */
export function removeSession(sessionId: number) {
  return http.del<void>(`/lms/training/session/${sessionId}`);
}

/** 批量必修分配（幂等） */
export function assignLearners(
  classId: number,
  body: { scopeType?: string; scopeValue?: string; userIds: number[] },
) {
  return http.post<AssignmentResult>(`/lms/training/${classId}/assign`, body);
}

/** 讲师课时台账 */
export function lecturerHours(classId: number | string) {
  return http.get<LecturerHoursItem[]>(`/lms/training/${classId}/hours`);
}

/** 自助签到（时间窗内） */
export function checkin(sessionId: number) {
  return http.post<CheckinItem>(`/lms/training/session/${sessionId}/checkin`);
}

/** 补签（需留原因） */
export function supplementCheckin(sessionId: number, body: { userId: number; username?: string; remark: string }) {
  return http.post<CheckinItem>(`/lms/training/session/${sessionId}/checkin/supplement`, body);
}

/** 签到明细 */
export function listCheckins(classId: number | string, sessionId?: number) {
  return http.get<CheckinItem[]>(`/lms/training/${classId}/checkin`, { sessionId });
}

/** 完成认定（按签到与课程进度实时计算） */
export function trainingCompletion(classId: number | string) {
  return http.get<TrainingCompletion>(`/lms/training/${classId}/completion`);
}
