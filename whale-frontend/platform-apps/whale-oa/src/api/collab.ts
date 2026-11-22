// OA 协同 API 层：会议室/会议/纪要/公告/任务/评论
//
// 后端字段：时间字段序列化为 ISO 字符串；日期字段为 YYYY-MM-DD。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 会议状态 */
export type OaMeetingStatus = 'SCHEDULED' | 'FINISHED' | 'CANCELLED';

/** 参会状态 */
export type OaParticipantStatus = 'INVITED' | 'ATTENDED' | 'ABSENT';

/** 公告范围 */
export type OaNoticeScopeType = 'ALL' | 'DEPT' | 'USER';

/** 公告状态 */
export type OaNoticeStatus = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN';

/** 任务状态 */
export type OaTaskStatus = 'TODO' | 'DOING' | 'DONE' | 'CANCELLED';

/** 任务优先级 */
export type OaTaskPriority = 'HIGH' | 'NORMAL' | 'LOW';

/** 任务来源 */
export type OaTaskSource = 'PERSONAL' | 'MEETING' | 'NOTICE';

/** 协同分页查询 */
export interface OaCollabQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  priority?: OaTaskPriority | '';
  ownerId?: number;
  roomId?: number;
  scopeType?: OaNoticeScopeType | '';
  source?: OaTaskSource | '';
  participantId?: number;
  dateFrom?: string;
  dateTo?: string;
  userId?: number;
}

/** 会议室 */
export interface OaMeetingRoom {
  id: number;
  name: string;
  location?: string;
  capacity?: number;
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
  createTime?: string;
}

/** 会议室入参 */
export interface OaMeetingRoomInput {
  name: string;
  location?: string;
  capacity?: number;
  status?: string;
  remark?: string;
}

/** 参会人 */
export interface OaMeetingParticipant {
  id: number;
  userId: number;
  userName?: string;
  required?: number;
  status: OaParticipantStatus;
  remark?: string;
}

/** 参会人入参 */
export interface OaMeetingParticipantInput {
  userId: number;
  userName?: string;
  required?: number;
  remark?: string;
}

/** 会议纪要 */
export interface OaMeetingMinute {
  id: number;
  meetingId: number;
  content: string;
  author?: string;
  authorId?: number;
  updateTime?: string;
}

/** 会议行动项 */
export interface OaMeetingAction {
  id: number;
  content: string;
  ownerId: number;
  ownerName?: string;
  dueDate?: string;
  status: 'OPEN' | 'DONE';
  taskId?: number;
}

/** 行动项入参 */
export interface OaMeetingActionInput {
  content: string;
  ownerId: number;
  ownerName?: string;
  dueDate?: string;
  createTask?: boolean;
}

/** 会议 */
export interface OaMeeting {
  id: number;
  code: string;
  title: string;
  roomId?: number;
  roomName?: string;
  organizer?: string;
  organizerId?: number;
  deptId?: number;
  startTime: string;
  endTime: string;
  agenda?: string;
  status: OaMeetingStatus;
  remark?: string;
  participantCount?: number;
  createTime?: string;
}

/** 会议详情 */
export interface OaMeetingDetail extends OaMeeting {
  participants: OaMeetingParticipant[];
  minute?: OaMeetingMinute;
  actions: OaMeetingAction[];
}

/** 会议入参 */
export interface OaMeetingInput {
  title: string;
  roomId?: number;
  organizer?: string;
  organizerId?: number;
  deptId?: number;
  startTime: string;
  endTime: string;
  agenda?: string;
  remark?: string;
  participants: OaMeetingParticipantInput[];
}

/** 纪要入参 */
export interface OaMeetingMinuteInput {
  content: string;
  author?: string;
  authorId?: number;
  actions?: OaMeetingActionInput[];
}

/** 公告接收人 */
export interface OaNoticeRecipient {
  id: number;
  userId: number;
  userName?: string;
  readFlag?: number;
  readTime?: string;
}

/** 公告 */
export interface OaNotice {
  id: number;
  title: string;
  content: string;
  scopeType: OaNoticeScopeType;
  scopeValue?: string;
  status: OaNoticeStatus;
  publisher?: string;
  publisherId?: number;
  publishTime?: string;
  remark?: string;
  createTime?: string;
  myReadFlag?: number;
  myReadTime?: string;
  recipientCount?: number;
  readCount?: number;
}

/** 公告详情 */
export interface OaNoticeDetail extends OaNotice {
  recipients: OaNoticeRecipient[];
}

/** 公告接收人入参 */
export interface OaNoticeRecipientInput {
  userId: number;
  userName?: string;
}

/** 公告入参 */
export interface OaNoticeInput {
  title: string;
  content: string;
  scopeType?: OaNoticeScopeType;
  scopeValue?: string;
  remark?: string;
  recipients?: OaNoticeRecipientInput[];
}

/** 通讯录候选 */
export interface OaUserCandidate {
  userId: number;
  username?: string;
  nickname?: string;
  deptId?: number;
}

/** 任务 */
export interface OaTask {
  id: number;
  title: string;
  description?: string;
  ownerId: number;
  ownerName?: string;
  creatorId?: number;
  creatorName?: string;
  priority: OaTaskPriority;
  status: OaTaskStatus;
  dueDate?: string;
  source: OaTaskSource;
  sourceId?: number;
  finishTime?: string;
  createTime?: string;
}

/** 任务入参 */
export interface OaTaskInput {
  title: string;
  description?: string;
  ownerId: number;
  ownerName?: string;
  priority?: OaTaskPriority;
  dueDate?: string;
  source?: OaTaskSource;
  sourceId?: number;
}

/** 任务评论 */
export interface OaTaskComment {
  id: number;
  taskId: number;
  userId?: number;
  userName?: string;
  content: string;
  createTime?: string;
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

// ---------------------------------------------------------------- 会议室

/** 分页查询会议室 */
export function pageRooms(params: OaCollabQuery = {}): Promise<PageResult<OaMeetingRoom>> {
  return http.get<PageResult<OaMeetingRoom>>(`/oa/v1/meeting/rooms/page${buildQuery(params)}`);
}

/** 查询全部启用会议室 */
export function listRooms(): Promise<OaMeetingRoom[]> {
  return http.get<OaMeetingRoom[]>('/oa/v1/meeting/rooms/all');
}

/** 新增会议室 */
export function createRoom(input: OaMeetingRoomInput): Promise<OaMeetingRoom> {
  return http.post<OaMeetingRoom>('/oa/v1/meeting/rooms', input);
}

/** 更新会议室 */
export function updateRoom(id: number, input: OaMeetingRoomInput): Promise<OaMeetingRoom> {
  return http.put<OaMeetingRoom>(`/oa/v1/meeting/rooms/${id}`, input);
}

/** 删除会议室 */
export function deleteRoom(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/meeting/rooms/${id}`);
}

// ---------------------------------------------------------------- 会议与纪要

/** 分页查询会议 */
export function pageMeetings(params: OaCollabQuery = {}): Promise<PageResult<OaMeeting>> {
  return http.get<PageResult<OaMeeting>>(`/oa/v1/meeting/page${buildQuery(params)}`);
}

/** 会议详情 */
export function getMeeting(id: number): Promise<OaMeetingDetail> {
  return http.get<OaMeetingDetail>(`/oa/v1/meeting/${id}`);
}

/** 创建会议 */
export function createMeeting(input: OaMeetingInput): Promise<OaMeetingDetail> {
  return http.post<OaMeetingDetail>('/oa/v1/meeting', input);
}

/** 更新会议 */
export function updateMeeting(id: number, input: OaMeetingInput): Promise<OaMeetingDetail> {
  return http.put<OaMeetingDetail>(`/oa/v1/meeting/${id}`, input);
}

/** 变更会议状态 */
export function changeMeetingStatus(id: number, status: OaMeetingStatus): Promise<boolean> {
  return http.put<boolean>(`/oa/v1/meeting/${id}/status`, { status });
}

/** 登记参会状态 */
export function changeParticipantStatus(
  meetingId: number,
  userId: number,
  status: OaParticipantStatus,
): Promise<OaMeetingParticipant> {
  return http.put<OaMeetingParticipant>(
    `/oa/v1/meeting/${meetingId}/participants/${userId}/status`,
    {
      status,
    },
  );
}

/** 保存会议纪要（含行动项） */
export function saveMeetingMinute(
  id: number,
  input: OaMeetingMinuteInput,
): Promise<OaMeetingDetail> {
  return http.put<OaMeetingDetail>(`/oa/v1/meeting/${id}/minute`, input);
}

// ---------------------------------------------------------------- 公告

/** 分页查询公告 */
export function pageNotices(params: OaCollabQuery = {}): Promise<PageResult<OaNotice>> {
  return http.get<PageResult<OaNotice>>(`/oa/v1/notice/page${buildQuery(params)}`);
}

/** 分页查询我的公告 */
export function pageMyNotices(params: OaCollabQuery = {}): Promise<PageResult<OaNotice>> {
  return http.get<PageResult<OaNotice>>(`/oa/v1/notice/my/page${buildQuery(params)}`);
}

/** 公告详情 */
export function getNotice(id: number): Promise<OaNoticeDetail> {
  return http.get<OaNoticeDetail>(`/oa/v1/notice/${id}`);
}

/** 接收人候选 */
export function listNoticeRecipients(
  params: {
    keyword?: string;
    deptId?: number;
    limit?: number;
  } = {},
): Promise<OaUserCandidate[]> {
  return http.get<OaUserCandidate[]>(`/oa/v1/notice/recipients${buildQuery(params)}`);
}

/** 新建公告 */
export function createNotice(input: OaNoticeInput): Promise<OaNotice> {
  return http.post<OaNotice>('/oa/v1/notice', input);
}

/** 更新公告 */
export function updateNotice(id: number, input: OaNoticeInput): Promise<OaNotice> {
  return http.put<OaNotice>(`/oa/v1/notice/${id}`, input);
}

/** 发布公告 */
export function publishNotice(id: number): Promise<OaNotice> {
  return http.put<OaNotice>(`/oa/v1/notice/${id}/publish`);
}

/** 撤回公告 */
export function withdrawNotice(id: number): Promise<OaNotice> {
  return http.put<OaNotice>(`/oa/v1/notice/${id}/withdraw`);
}

/** 标记公告已读 */
export function markNoticeRead(id: number): Promise<void> {
  return http.put<void>(`/oa/v1/notice/${id}/read`);
}

/** 公告已读统计 */
export function getNoticeStats(id: number): Promise<OaNoticeDetail> {
  return http.get<OaNoticeDetail>(`/oa/v1/notice/${id}/stats`);
}

// ---------------------------------------------------------------- 任务

/** 分页查询任务 */
export function pageTasks(
  params: OaCollabQuery & { mine?: boolean } = {},
): Promise<PageResult<OaTask>> {
  return http.get<PageResult<OaTask>>(`/oa/v1/task/page${buildQuery(params)}`);
}

/** 任务详情 */
export function getTask(id: number): Promise<OaTask> {
  return http.get<OaTask>(`/oa/v1/task/${id}`);
}

/** 创建任务 */
export function createTask(input: OaTaskInput): Promise<OaTask> {
  return http.post<OaTask>('/oa/v1/task', input);
}

/** 更新任务 */
export function updateTask(id: number, input: OaTaskInput): Promise<OaTask> {
  return http.put<OaTask>(`/oa/v1/task/${id}`, input);
}

/** 删除任务 */
export function deleteTask(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/task/${id}`);
}

/** 变更任务状态 */
export function changeTaskStatus(id: number, status: OaTaskStatus): Promise<boolean> {
  return http.put<boolean>(`/oa/v1/task/${id}/status`, { status });
}

/** 任务评论列表 */
export function listTaskComments(id: number): Promise<OaTaskComment[]> {
  return http.get<OaTaskComment[]>(`/oa/v1/task/${id}/comments`);
}

/** 新增任务评论 */
export function addTaskComment(id: number, content: string): Promise<OaTaskComment> {
  return http.post<OaTaskComment>(`/oa/v1/task/${id}/comments`, { content });
}

/** 删除任务评论 */
export function deleteTaskComment(id: number, commentId: number): Promise<void> {
  return http.del<void>(`/oa/v1/task/${id}/comments/${commentId}`);
}
