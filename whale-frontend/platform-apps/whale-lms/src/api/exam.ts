// LMS 考试域接口封装（M3-2：考试 / 作答 / 交卷判分 / 人工判分 / 成绩）。
//
// 边界口径：
// 1. 卷面接口不返回标准答案与选项正确标记，交卷后才按题给出对错；
// 2. 题序由服务端按作答 seed 派生，前端不得自行排序（打乱后与判分无关，但会让
//    学员「刷新一次题就变」，体验上像作弊）；
// 3. 成绩按版本留存，人工判分后学员看到的是最新版本。

import { http } from './client';

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 考试 */
export interface ExamItem {
  id: number;
  examCode: string;
  title: string;
  paperId: number;
  paperTitle?: string;
  paperTotalScore?: number;
  courseId?: number;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts: number;
  shuffle: boolean;
  passScore: number;
  switchLimit: number;
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED';
  remark?: string;
  createTime?: string;
}

/** 考试保存请求体 */
export interface ExamSaveBody {
  examCode?: string;
  title: string;
  paperId: number;
  courseId?: number;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts: number;
  shuffle?: boolean;
  passScore?: number;
  switchLimit?: number;
  remark?: string;
}

/** 作答记录 */
export interface AttemptItem {
  id: number;
  examId: number;
  examTitle?: string;
  paperId: number;
  userId: number;
  username?: string;
  attemptNo: number;
  startedAt: string;
  deadlineAt: string;
  submittedAt?: string;
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED' | 'EXPIRED';
  objectiveScore?: number;
  subjectiveScore?: number;
  totalScore?: number;
  passed?: boolean;
  switchCount?: number;
  pendingGradeCount?: number;
}

/** 卷面选项（无正确标记） */
export interface AttemptOption {
  optionKey: string;
  content: string;
}

/** 卷面单题 */
export interface AttemptQuestion {
  questionId: number;
  type: 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'SHORT_ANSWER';
  stem: string;
  score: number;
  sortNo: number;
  answer?: string;
  options: AttemptOption[];
}

/** 作答卷面 */
export interface AttemptPaper {
  attemptId: number;
  examId: number;
  examTitle?: string;
  paperId: number;
  durationMinutes?: number;
  startedAt: string;
  deadlineAt: string;
  submittedAt?: string;
  status: string;
  remainingSeconds: number;
  totalScore: number;
  passScore: number;
  shuffle: boolean;
  questions: AttemptQuestion[];
}

/** 交卷结果单题 */
export interface ResultQuestion {
  questionId: number;
  type: string;
  stem?: string;
  maxScore: number;
  score?: number;
  answer?: string;
  correct?: boolean;
  graded: boolean;
}

/** 交卷结果 */
export interface AttemptResult {
  attemptId: number;
  examId: number;
  examTitle?: string;
  attemptNo: number;
  status: string;
  objectiveScore?: number;
  subjectiveScore?: number;
  totalScore?: number;
  passScore?: number;
  passed?: boolean;
  submittedAt?: string;
  pendingGrade: boolean;
  scoreVersion?: number;
  questions: ResultQuestion[];
}

/** 成绩 */
export interface ScoreItem {
  id: number;
  attemptId: number;
  examId: number;
  examTitle?: string;
  userId: number;
  versionNo: number;
  objectiveScore: number;
  subjectiveScore: number;
  totalScore: number;
  passed: boolean;
  gradedBy?: number;
  gradedAt?: string;
  remark?: string;
}

/** 判分明细 */
export interface GradeAnswer {
  questionId: number;
  type: string;
  stem?: string;
  standardAnswer?: string;
  answer?: string;
  maxScore: number;
  score?: number;
  graded: boolean;
  objective: boolean;
}

/** 判分视图 */
export interface GradeDetail {
  attemptId: number;
  examId: number;
  examTitle?: string;
  userId: number;
  username?: string;
  attemptNo: number;
  status: string;
  submittedAt?: string;
  switchCount?: number;
  objectiveScore?: number;
  subjectiveScore?: number;
  totalScore?: number;
  pendingGradeCount: number;
  answers: GradeAnswer[];
}

/** 考试分页 */
export function pageExams(params: {
  keyword?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<ExamItem>>('/lms/v1/exam/page', { ...params });
}

/** 考试详情 */
export function examDetail(id: number | string) {
  return http.get<ExamItem>(`/lms/v1/exam/${id}`);
}

/** 新建考试 */
export function createExam(body: ExamSaveBody) {
  return http.post<number>('/lms/v1/exam', body);
}

/** 编辑考试（仅草稿） */
export function updateExam(id: number, body: ExamSaveBody) {
  return http.put<void>(`/lms/v1/exam/${id}`, body);
}

/** 删除考试（仅草稿） */
export function deleteExam(id: number) {
  return http.del<void>(`/lms/v1/exam/${id}`);
}

/** 发布考试 */
export function publishExam(id: number) {
  return http.put<void>(`/lms/v1/exam/${id}/publish`);
}

/** 结课 */
export function closeExam(id: number) {
  return http.put<void>(`/lms/v1/exam/${id}/close`);
}

/** 开始作答（存在未交卷记录时复用） */
export function startAttempt(examId: number) {
  return http.post<number>(`/lms/v1/attempt/start/${examId}`);
}

/** 获取作答卷面 */
export function attemptPaper(attemptId: number | string) {
  return http.get<AttemptPaper>(`/lms/v1/attempt/${attemptId}/paper`);
}

/** 保存答案（整卷或增量） */
export function saveAnswers(
  attemptId: number | string,
  body: { answers: { questionId: number; answer?: string }[]; switchCount?: number },
) {
  return http.put<number>(`/lms/v1/attempt/${attemptId}/answers`, body);
}

/** 交卷 */
export function submitAttempt(attemptId: number | string) {
  return http.post<AttemptResult>(`/lms/v1/attempt/${attemptId}/submit`);
}

/** 我的作答记录 */
export function myAttempts(params: {
  examId?: number;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<AttemptItem>>('/lms/v1/attempt/mine', { ...params });
}

/** 我的成绩 */
export function myScores(params: { current?: number; size?: number }) {
  return http.get<PageData<ScoreItem>>('/lms/v1/attempt/scores/mine', { ...params });
}

/** 某次作答结果 */
export function attemptResult(attemptId: number | string) {
  return http.get<AttemptResult>(`/lms/v1/attempt/${attemptId}/result`);
}

/** 待判队列 */
export function pendingGrades(params: { examId?: number; current?: number; size?: number }) {
  return http.get<PageData<AttemptItem>>('/lms/v1/grade/pending', { ...params });
}

/** 判分明细 */
export function gradeDetail(attemptId: number | string) {
  return http.get<GradeDetail>(`/lms/v1/grade/${attemptId}`);
}

/** 提交判分 */
export function submitGrade(
  attemptId: number | string,
  body: { items: { questionId: number; score: number }[]; remark?: string },
) {
  return http.post<ScoreItem>(`/lms/v1/grade/${attemptId}`, body);
}
