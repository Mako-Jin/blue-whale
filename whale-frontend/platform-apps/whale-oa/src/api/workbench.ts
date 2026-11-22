// OA 协同 - 工作台 API 层：汇总待办审批、未读公告、今日会议、到期任务与近期生日
//
// 后端时间字段序列化为 ISO 字符串，日期字段为 YYYY-MM-DD。

import { http } from './client';
import type { OaTaskPriority, OaTaskStatus } from './collab';

/** 今日会议条目 */
export interface OaWorkbenchMeeting {
  id: number;
  title: string;
  startTime: string;
  endTime: string;
  roomName?: string;
}

/** 到期任务条目 */
export interface OaWorkbenchTask {
  id: number;
  title: string;
  dueDate: string;
  status: OaTaskStatus;
  priority: OaTaskPriority;
}

/** 近期生日条目 */
export interface OaWorkbenchBirthday {
  userId: number;
  employeeNo?: string;
  birthday: string;
  daysAhead: number;
  deptId?: number;
}

/** 工作台汇总 */
export interface OaWorkbenchSummary {
  pendingApprovalCount: number;
  unreadNoticeCount: number;
  todayMeetings: OaWorkbenchMeeting[];
  dueTasks: OaWorkbenchTask[];
  birthdays: OaWorkbenchBirthday[];
}

/** 查询工作台汇总 */
export function getWorkbenchSummary(): Promise<OaWorkbenchSummary> {
  return http.get<OaWorkbenchSummary>('/oa/v1/workbench/summary');
}
