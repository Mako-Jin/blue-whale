// LMS 课程域接口封装（M1：课程 / 章节 / 课件 / 报名 / 进度）。
//
// 后端：whale-lms 9008，网关自持 /lms 一级前缀（REQ-ARCH-003）。

import { http } from './client';

/** 课程 */
export interface CourseItem {
  id: number;
  courseCode: string;
  title: string;
  category?: string;
  description?: string;
  lecturer?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  createTime?: string;
}

/** 课件 */
export interface WareItem {
  id: number;
  name: string;
  wareType: 'DOCUMENT' | 'VIDEO' | 'LINK';
  fileRefId?: string;
  linkUrl?: string;
  sortNo?: number;
}

/** 章节（含课件） */
export interface ChapterItem {
  id: number;
  title: string;
  sortNo?: number;
  remark?: string;
  wares: WareItem[];
}

/** 课程详情 */
export interface CourseDetail {
  course: CourseItem;
  chapters: ChapterItem[];
  chapterCount: number;
}

/** 我的学习记录 */
export interface EnrollmentItem {
  id: number;
  courseId: number;
  courseTitle?: string;
  courseStatus?: string;
  enrolledAt?: string;
  overallPercent?: number;
  chapters?: { chapterId: number; chapterTitle: string; percent: number }[];
}

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 课程分页 */
export function pageCourses(params: {
  keyword?: string;
  category?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<CourseItem>>('/lms/v1/course/page', { ...params });
}

/** 课程详情 */
export function courseDetail(id: number | string) {
  return http.get<CourseDetail>(`/lms/v1/course/${id}`);
}

/** 新建课程 */
export function createCourse(body: {
  courseCode?: string;
  title: string;
  category?: string;
  description?: string;
  lecturer?: string;
}) {
  return http.post<number>('/lms/v1/course', body);
}

/** 编辑课程 */
export function updateCourse(
  id: number,
  body: { title: string; category?: string; description?: string; lecturer?: string },
) {
  return http.put<void>(`/lms/v1/course/${id}`, body);
}

/** 课程发布 / 归档 */
export function changeCourseStatus(id: number, action: 'PUBLISH' | 'ARCHIVE') {
  return http.put<void>(`/lms/v1/course/${id}/status`, { action });
}

/** 删除课程 */
export function deleteCourse(id: number) {
  return http.del<void>(`/lms/v1/course/${id}`);
}

/** 新增章节 */
export function addChapter(
  courseId: number,
  body: { title: string; sortNo?: number; remark?: string },
) {
  return http.post<number>(`/lms/v1/course/${courseId}/chapter`, body);
}

/** 删除章节 */
export function removeChapter(chapterId: number) {
  return http.del<void>(`/lms/v1/course/chapter/${chapterId}`);
}

/** 新增课件 */
export function addWare(
  chapterId: number,
  body: { name: string; wareType: string; fileRefId?: string; linkUrl?: string },
) {
  return http.post<number>(`/lms/v1/course/chapter/${chapterId}/ware`, body);
}

/** 删除课件 */
export function removeWare(wareId: number) {
  return http.del<void>(`/lms/v1/course/ware/${wareId}`);
}

/** 报名（幂等） */
export function enroll(courseId: number | string) {
  return http.post<EnrollmentItem>(`/lms/v1/learning/enroll/${courseId}`);
}

/** 我的学习分页 */
export function myEnrollments(params: { current?: number; size?: number }) {
  return http.get<PageData<EnrollmentItem>>('/lms/v1/learning/my', { ...params });
}

/** 上报学习进度（只增不减） */
export function reportProgress(courseId: number | string, chapterId: number, percent: number) {
  return http.post<EnrollmentItem>(`/lms/v1/learning/${courseId}/progress`, { chapterId, percent });
}
