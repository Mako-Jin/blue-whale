// OA 文化协同 API 层：文化动态、生日/周年祝福、投票问卷、建议箱
import { http } from './client';
import type { PageResult } from './client';

/** 文化动态类型 */
export type CultureEventType = 'MILESTONE' | 'ACTIVITY' | 'NEWS';

/** 文化动态状态 */
export type CultureEventStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/** 祝福类型：生日 / 入职周年 */
export type CultureWishType = 'BIRTHDAY' | 'ANNIVERSARY';

/** 投票状态 */
export type PollStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

/** 建议类别 */
export type SuggestionCategory = 'SUGGESTION' | 'COMPLAINT' | 'PRAISE' | 'OTHER';

/** 建议状态 */
export type SuggestionStatus = 'PENDING' | 'PROCESSING' | 'REPLIED' | 'CLOSED';

/** 文化动态 */
export interface CultureEvent {
  id: number;
  eventType: CultureEventType;
  title: string;
  content?: string;
  eventDate?: string;
  coverFileId?: number;
  status: CultureEventStatus;
  publisherId?: number;
  publisherName?: string;
  publishTime?: string;
  remark?: string;
  createTime?: string;
}

/** 文化动态保存入参 */
export interface CultureEventInput {
  title: string;
  eventType: CultureEventType;
  content?: string;
  eventDate?: string;
  remark?: string;
}

/** 今日生日 / 周年（由员工档案派生） */
export interface BirthdayCelebration {
  userId: number;
  userName?: string;
  deptId?: number;
  birthday?: string;
  hireDate?: string;
  years?: number;
  type: CultureWishType;
}

/** 祝福记录 */
export interface CultureWish {
  id: number;
  wishType: CultureWishType;
  targetUserId: number;
  targetName?: string;
  content: string;
  senderId?: number;
  senderName?: string;
  wishDate?: string;
  createTime?: string;
}

/** 送祝福入参 */
export interface CultureWishInput {
  wishType: CultureWishType;
  targetUserId: number;
  targetName?: string;
  content: string;
}

/** 投票选项 */
export interface PollOption {
  id: number;
  optionText: string;
  sortNo?: number;
  voteCount?: number;
  myChoice?: boolean;
}

/** 投票选项保存入参 */
export interface PollOptionInput {
  optionText: string;
  sortNo?: number;
}

/** 投票问卷 */
export interface Poll {
  id: number;
  title: string;
  description?: string;
  multipleChoice?: boolean;
  anonymous?: boolean;
  maxChoices?: number;
  startTime?: string;
  endTime?: string;
  status: PollStatus;
  creatorId?: number;
  creatorName?: string;
  createTime?: string;
}

/** 投票详情：附带选项票数与我已投票标记 */
export interface PollDetail extends Poll {
  options: PollOption[];
  myVoted?: boolean;
  totalVotes?: number;
}

/** 投票保存入参 */
export interface PollInput {
  title: string;
  description?: string;
  multipleChoice?: boolean;
  anonymous?: boolean;
  maxChoices?: number;
  startTime?: string;
  endTime?: string;
  options: PollOptionInput[];
}

/** 建议 */
export interface Suggestion {
  id: number;
  title: string;
  category: SuggestionCategory;
  content?: string;
  anonymous?: boolean;
  submitterId?: number | null;
  submitterName?: string | null;
  deptId?: number;
  status: SuggestionStatus;
  handlerId?: number;
  handlerName?: string;
  handledTime?: string;
  createTime?: string;
}

/** 建议回复 */
export interface SuggestionReply {
  id: number;
  replierId?: number;
  replierName?: string;
  content: string;
  createTime?: string;
}

/** 建议详情：附带回复列表 */
export interface SuggestionDetail extends Suggestion {
  replies: SuggestionReply[];
}

/** 提交建议入参 */
export interface SuggestionInput {
  title: string;
  category: SuggestionCategory;
  content: string;
  anonymous?: boolean;
  deptId?: number;
}

/** 文化动态分页查询 */
export interface CultureEventQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: CultureEventStatus;
  eventType?: CultureEventType;
}

/** 祝福分页查询 */
export interface CultureWishQuery {
  current?: number;
  size?: number;
  wishType?: CultureWishType;
  targetUserId?: number;
}

/** 投票分页查询 */
export interface PollQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: PollStatus;
}

/** 建议分页查询 */
export interface SuggestionQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: SuggestionStatus;
  category?: SuggestionCategory;
  mine?: boolean;
}

/** 拼装查询串：跳过 undefined/null/空串，保留 false */
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

/** 分页查询文化动态 */
export function pageCultureEvents(
  params: CultureEventQuery = {},
): Promise<PageResult<CultureEvent>> {
  return http.get<PageResult<CultureEvent>>(`/oa/v1/culture/events/page${buildQuery(params)}`);
}

/** 文化动态详情 */
export function getCultureEvent(id: number): Promise<CultureEvent> {
  return http.get<CultureEvent>(`/oa/v1/culture/events/${id}`);
}

/** 新增文化动态 */
export function createCultureEvent(input: CultureEventInput): Promise<CultureEvent> {
  return http.post<CultureEvent>('/oa/v1/culture/events', input);
}

/** 编辑文化动态 */
export function updateCultureEvent(id: number, input: CultureEventInput): Promise<CultureEvent> {
  return http.put<CultureEvent>(`/oa/v1/culture/events/${id}`, input);
}

/** 删除文化动态 */
export function deleteCultureEvent(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/culture/events/${id}`);
}

/** 发布文化动态 */
export function publishCultureEvent(id: number): Promise<CultureEvent> {
  return http.put<CultureEvent>(`/oa/v1/culture/events/${id}/publish`);
}

/** 查询指定日期的生日 / 周年（缺省为今天） */
export function listBirthdays(date?: string): Promise<BirthdayCelebration[]> {
  return http.get<BirthdayCelebration[]>(`/oa/v1/culture/birthdays${buildQuery({ date })}`);
}

/** 分页查询祝福 */
export function pageCultureWishes(params: CultureWishQuery = {}): Promise<PageResult<CultureWish>> {
  return http.get<PageResult<CultureWish>>(`/oa/v1/culture/wishes/page${buildQuery(params)}`);
}

/** 送祝福 */
export function createCultureWish(input: CultureWishInput): Promise<CultureWish> {
  return http.post<CultureWish>('/oa/v1/culture/wishes', input);
}

/** 分页查询投票 */
export function pagePolls(params: PollQuery = {}): Promise<PageResult<Poll>> {
  return http.get<PageResult<Poll>>(`/oa/v1/poll/page${buildQuery(params)}`);
}

/** 投票详情（含选项票数与我的选择） */
export function getPoll(id: number): Promise<PollDetail> {
  return http.get<PollDetail>(`/oa/v1/poll/${id}`);
}

/** 新增投票 */
export function createPoll(input: PollInput): Promise<Poll> {
  return http.post<Poll>('/oa/v1/poll', input);
}

/** 编辑投票 */
export function updatePoll(id: number, input: PollInput): Promise<Poll> {
  return http.put<Poll>(`/oa/v1/poll/${id}`, input);
}

/** 删除投票 */
export function deletePoll(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/poll/${id}`);
}

/** 发布投票 */
export function publishPoll(id: number): Promise<Poll> {
  return http.put<Poll>(`/oa/v1/poll/${id}/publish`);
}

/** 关闭投票 */
export function closePoll(id: number): Promise<Poll> {
  return http.put<Poll>(`/oa/v1/poll/${id}/close`);
}

/** 参与投票 */
export function votePoll(id: number, optionIds: number[]): Promise<void> {
  return http.post<void>(`/oa/v1/poll/${id}/vote`, { optionIds });
}

/** 分页查询建议 */
export function pageSuggestions(params: SuggestionQuery = {}): Promise<PageResult<Suggestion>> {
  return http.get<PageResult<Suggestion>>(`/oa/v1/suggestion/page${buildQuery(params)}`);
}

/** 建议详情（含回复） */
export function getSuggestion(id: number): Promise<SuggestionDetail> {
  return http.get<SuggestionDetail>(`/oa/v1/suggestion/${id}`);
}

/** 提交建议 */
export function createSuggestion(input: SuggestionInput): Promise<Suggestion> {
  return http.post<Suggestion>('/oa/v1/suggestion', input);
}

/** 回复建议 */
export function replySuggestion(
  id: number,
  input: { content: string; status?: SuggestionStatus },
): Promise<SuggestionDetail> {
  return http.post<SuggestionDetail>(`/oa/v1/suggestion/${id}/reply`, input);
}

/** 关闭建议 */
export function closeSuggestion(id: number): Promise<Suggestion> {
  return http.put<Suggestion>(`/oa/v1/suggestion/${id}/close`);
}
