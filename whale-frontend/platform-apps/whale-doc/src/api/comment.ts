// 页面评论 API

import { http } from './client';
import type { DocComment, DocCommentUnread } from './types';

/** 新增评论参数 */
export interface CommentCreatePayload {
  content: string;
  parentId?: number;
  anchorBlockId?: string;
  anchorText?: string;
}

/** 评论接口 */
export const commentApi = {
  list: (pageId: number, resolved?: boolean) =>
    http.get<DocComment[]>(
      `/doc/page/${pageId}/comment`,
      resolved === undefined ? {} : { resolved },
    ),
  create: (pageId: number, payload: CommentCreatePayload) =>
    http.post<DocComment>(`/doc/page/${pageId}/comment`, payload),
  resolve: (commentId: number, resolved: boolean) =>
    http.put<DocComment>(`/doc/comment/${commentId}/resolve?resolved=${resolved}`, {}),
  remove: (commentId: number) => http.del<void>(`/doc/comment/${commentId}`),
  unread: (pageId: number) => http.get<DocCommentUnread>(`/doc/page/${pageId}/comment/unread`),
  markRead: (pageId: number) => http.post<DocCommentUnread>(`/doc/page/${pageId}/comment/read`),
};
