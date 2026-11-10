// LMS 集成域接口封装（M5：员工快照 / 通知日志 / 催办 / 请假冲突）。
//
// 幂等口径由服务端保证：同步按员工编号 upsert，催办按「培训班 + 日期」去重，
// 同一天重复触发不会重复发通知。

import { http } from './client';

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 员工快照 */
export interface MemberSnapshotItem {
  id: number;
  employeeNo: string;
  userId?: number;
  name?: string;
  orgCode?: string;
  position?: string;
  status: 'ACTIVE' | 'LEFT';
  hireDate?: string;
  leaveDate?: string;
  lastSyncedAt?: string;
  urgeable: boolean;
}

/** 同步结果 */
export interface MemberSyncResult {
  syncedAt: string;
  sourceTotal: number;
  created: number;
  updated: number;
  skipped: number;
  snapshotTotal: number;
}

/** 通知场景 */
export type NotifySceneType = 'ENROLL' | 'CLASS_START' | 'URGE' | 'CERT_ISSUE';

/** 通知投递日志 */
export interface NotifyLogItem {
  id: number;
  scene: NotifySceneType;
  idempotencyKey: string;
  bizType?: string;
  bizId?: number;
  userId: number;
  templateCode?: string;
  title?: string;
  channel?: string;
  sendStatus: 'SENT' | 'FAILED';
  failReason?: string;
  sentAt: string;
}

/** 单班催办结果 */
export interface UrgeItem {
  classId: number;
  classCode?: string;
  className?: string;
  pendingCount: number;
  notified: number;
  skippedOffboard: number;
  skippedUnrecorded: number;
  error?: string;
}

/** 一轮催办结果 */
export interface UrgeRunResult {
  day: string;
  scannedClasses: number;
  urgentClasses: number;
  notified: number;
  skippedOffboard: number;
  skippedUnrecorded: number;
  failedClasses: number;
  items: UrgeItem[];
}

/** 场次与请假冲突 */
export interface ScheduleConflictItem {
  classId: number;
  sessionId: number;
  sessionNo?: number;
  sessionTitle?: string;
  startTime?: string;
  endTime?: string;
  userId: number;
  username?: string;
  leaveCode?: string;
  leaveType?: string;
  leaveStart?: string;
  leaveEnd?: string;
}

/** 同步员工目录 */
export function syncMembers() {
  return http.post<MemberSyncResult>('/lms/integration/member/sync');
}

/** 员工快照分页 */
export function pageMembers(params: {
  keyword?: string;
  orgCode?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<MemberSnapshotItem>>('/lms/integration/member/page', { ...params });
}

/** 通知日志分页 */
export function pageNotifyLogs(params: {
  scene?: string;
  userId?: number;
  sendStatus?: string;
  idempotencyKey?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<NotifyLogItem>>('/lms/integration/notify/page', { ...params });
}

/** 手动执行催办（可按天补跑） */
export function runUrge(day?: string) {
  return http.post<UrgeRunResult>(`/lms/urge/run${day ? `?day=${day}` : ''}`);
}

/** 预览单个培训班会催到谁 */
export function previewUrge(classId: number) {
  return http.get<UrgeItem>('/lms/urge/preview', { classId });
}

/** 查询培训班场次与请假冲突 */
export function leaveConflicts(classId: number) {
  return http.get<ScheduleConflictItem[]>('/lms/urge/leave-conflicts', { classId });
}
