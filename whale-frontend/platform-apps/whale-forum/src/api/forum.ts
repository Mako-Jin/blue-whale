// 论坛接口（M0 只有门面 meta；M1 起按 F-01..F-05 补齐版块/帖子/楼层/互动）

import { request } from './client';

/** 非功能上限（与后端 ForumMetaVO.Limits 一一对应） */
export interface ForumLimits {
  maxTitleLength: number;
  maxContentLength: number;
  maxPostsPerThread: number;
  pageSizeDefault: number;
  pageSizeMax: number;
  maxPageNumber: number;
  maxMentionsPerPost: number;
  postIntervalSeconds: number;
  replyIntervalSeconds: number;
  hotRefreshMinutes: number;
}

/** 门面信息（枚举由服务端下发，前端不硬编码） */
export interface ForumMeta {
  service: string;
  version: string;
  threadTypes: string[];
  threadStatuses: string[];
  boardVisibilities: string[];
  auditModes: string[];
  reportStatuses: string[];
  reactionTypes: string[];
  moderationActions: string[];
  anonymousEnabled: boolean;
  limits: ForumLimits;
}

/** 读取门面信息 */
export function fetchForumMeta(): Promise<ForumMeta> {
  return request<ForumMeta>('/forum/meta');
}
