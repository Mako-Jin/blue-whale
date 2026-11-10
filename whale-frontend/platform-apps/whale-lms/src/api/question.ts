// LMS 题库域接口封装（M3-1：题目 / 选项 / 启停）。
//
// 后端：whale-lms 9008，网关自持 /lms 一级前缀（REQ-ARCH-003）。
// 选项的 correct 标记由服务端按标准答案派生，前端不提交。

import { http } from './client';

/** 题目选项 */
export interface QuestionOptionItem {
  optionKey: string;
  content: string;
  correct: boolean;
  sortNo?: number;
}

/** 题目 */
export interface QuestionItem {
  id: number;
  questionCode: string;
  category: string;
  type: 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'SHORT_ANSWER';
  difficulty: 'EASY' | 'NORMAL' | 'HARD';
  tags?: string[];
  stem: string;
  standardAnswer?: string;
  analysis?: string;
  defaultScore: number;
  status: 'DRAFT' | 'ACTIVE' | 'DISABLED';
  options: QuestionOptionItem[];
  createTime?: string;
}

/** 题目保存载荷 */
export interface QuestionPayload {
  questionCode: string;
  category: string;
  type: string;
  difficulty?: string;
  tags?: string[];
  stem: string;
  standardAnswer?: string;
  analysis?: string;
  defaultScore: number;
  options?: Array<{ optionKey: string; content: string; sortNo?: number }>;
  activate?: boolean;
}

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 题目分页 */
export function pageQuestions(params: {
  keyword?: string;
  category?: string;
  type?: string;
  difficulty?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<QuestionItem>>('/lms/question/page', { ...params });
}

/** 题目详情 */
export function questionDetail(id: number | string) {
  return http.get<QuestionItem>(`/lms/question/${id}`);
}

/** 新建题目 */
export function createQuestion(body: QuestionPayload) {
  return http.post<number>('/lms/question', body);
}

/** 编辑题目 */
export function updateQuestion(id: number, body: QuestionPayload) {
  return http.put<void>(`/lms/question/${id}`, body);
}

/** 启用 / 停用 */
export function changeQuestionStatus(id: number, action: 'ACTIVATE' | 'DISABLE') {
  return http.put<void>(`/lms/question/${id}/status`, { action });
}

/** 删除题目（被试卷引用时后端拒绝） */
export function deleteQuestion(id: number) {
  return http.del<void>(`/lms/question/${id}`);
}
