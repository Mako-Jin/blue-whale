// LMS 试卷域接口封装（M3-1：试卷 / 试卷题目 / 规则抽题 / 发布归档）。
//
// 抽题可复现：seed 不传时服务端用试卷编码派生，重复生成结果一致；
// 传新 seed 才会重新洗牌。候选不足时后端直接报错，不会悄悄少抽。

import { http } from './client';

/** 试卷 */
export interface PaperItem {
  id: number;
  paperCode: string;
  title: string;
  courseId?: number;
  durationMinutes: number;
  totalScore: number;
  passScore: number;
  questionCount: number;
  seed?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  remark?: string;
  createTime?: string;
}

/** 试卷题目 */
export interface PaperQuestionItem {
  questionId: number;
  questionCode?: string;
  type?: string;
  stem?: string;
  score: number;
  sortNo: number;
  source: 'MANUAL' | 'RULE';
}

/** 试卷详情 */
export interface PaperDetail extends PaperItem {
  ruleConfig?: string;
  questions: PaperQuestionItem[];
}

/** 抽题规则 */
export interface PaperRule {
  category?: string;
  type?: string;
  difficulty?: string;
  count: number;
  score?: number;
}

/** 抽题结果 */
export interface PaperGenerateResult {
  picked: number;
  questionCount: number;
  totalScore: number;
  seed: string;
}

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 试卷分页 */
export function pagePapers(params: {
  keyword?: string;
  status?: string;
  courseId?: number;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<PaperItem>>('/lms/paper/page', { ...params });
}

/** 试卷详情 */
export function paperDetail(id: number | string) {
  return http.get<PaperDetail>(`/lms/paper/${id}`);
}

/** 新建试卷 */
export function createPaper(body: {
  paperCode: string;
  title: string;
  courseId?: number;
  durationMinutes: number;
  passScore?: number;
  remark?: string;
}) {
  return http.post<number>('/lms/paper', body);
}

/** 编辑试卷（仅草稿） */
export function updatePaper(
  id: number,
  body: { paperCode: string; title: string; courseId?: number; durationMinutes: number; passScore?: number; remark?: string },
) {
  return http.put<void>(`/lms/paper/${id}`, body);
}

/** 删除试卷（仅草稿） */
export function deletePaper(id: number) {
  return http.del<void>(`/lms/paper/${id}`);
}

/** 手工加题 */
export function addPaperQuestion(id: number, body: { questionId: number; score?: number }) {
  return http.post<void>(`/lms/paper/${id}/question`, body);
}

/** 移出题目 */
export function removePaperQuestion(id: number, questionId: number) {
  return http.del<void>(`/lms/paper/${id}/question/${questionId}`);
}

/** 规则抽题（同 seed 可复现） */
export function generatePaper(id: number, body: { seed?: string; rules: PaperRule[] }) {
  return http.post<PaperGenerateResult>(`/lms/paper/${id}/generate`, body);
}

/** 发布试卷 */
export function publishPaper(id: number) {
  return http.put<void>(`/lms/paper/${id}/publish`);
}

/** 归档试卷 */
export function archivePaper(id: number) {
  return http.put<void>(`/lms/paper/${id}/archive`);
}
