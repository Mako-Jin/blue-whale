// PDM 协同 API：项目交付物归档、研发评审、在线预览（经网关 /pdm/** 转发）
//
// 交付物只引用已发布对象版本；预览任务按文档版本与类型幂等，可重试。

import { download, http, type PageResult } from './client';

/** 交付物对象类型 */
export type DeliverableObjectType = 'DOCUMENT' | 'BOM' | 'ROUTE';

/** 交付物类型 */
export type DeliverableType = 'DESIGN' | 'DRAWING' | 'REPORT' | 'OTHER';

/** 项目交付物 */
export interface Deliverable {
  id: number;
  projectId: number;
  projectName?: string;
  workItemId?: number;
  workItemName?: string;
  objectType: DeliverableObjectType;
  objectId: number;
  objectNo?: string;
  objectTitle?: string;
  objectVersionNo: string;
  deliverableType: DeliverableType;
  status: string;
  archivedBy?: number;
  archivedAt?: string;
  remark?: string;
  createTime?: string;
}

/** 交付物查询条件 */
export interface DeliverableQuery {
  current?: number;
  size?: number;
  projectId?: number;
  objectType?: DeliverableObjectType;
  deliverableType?: DeliverableType;
  keyword?: string;
}

/** 交付物归档入参 */
export interface DeliverableInput {
  projectId: number;
  projectName?: string;
  workItemId?: number;
  workItemName?: string;
  objectType: DeliverableObjectType;
  objectId: number;
  objectVersionNo: string;
  deliverableType?: DeliverableType;
  remark?: string;
}

/** 分页查询交付物 */
export function pageDeliverables(query: DeliverableQuery): Promise<PageResult<Deliverable>> {
  return http.get<PageResult<Deliverable>>('/pdm/v1/collab/deliverable/page', { ...query });
}

/** 归档已发布对象版本 */
export function archiveDeliverable(input: DeliverableInput): Promise<Deliverable> {
  return http.post<Deliverable>('/pdm/v1/collab/deliverable', input);
}

/** 取消归档 */
export function deleteDeliverable(id: number): Promise<void> {
  return http.del<void>(`/pdm/v1/collab/deliverable/${id}`);
}

/** 评审类型 */
export type ReviewType = 'DESIGN' | 'PROTOTYPE' | 'TRANSFER';

/** 评审状态 */
export type ReviewStatus = 'DRAFT' | 'CLOSED';

/** 评审结论 */
export type ReviewConclusion = 'PASSED' | 'CONDITIONAL' | 'REJECTED';

/** 研发评审 */
export interface Review {
  id: number;
  reviewNo: string;
  reviewType: ReviewType;
  title: string;
  projectId?: number;
  projectName?: string;
  objectType?: string;
  objectId?: number;
  objectNo?: string;
  objectVersionNo?: string;
  participants?: string;
  conclusion?: ReviewConclusion;
  conclusionComment?: string;
  attachmentDocumentId?: number;
  attachmentVersionNo?: string;
  approvalInstanceId?: number;
  status: ReviewStatus;
  createdBy?: number;
  closedBy?: number;
  closedAt?: string;
  createTime?: string;
  updateTime?: string;
}

/** 评审查询条件 */
export interface ReviewQuery {
  current?: number;
  size?: number;
  projectId?: number;
  reviewType?: ReviewType;
  status?: ReviewStatus;
  keyword?: string;
}

/** 评审保存入参 */
export interface ReviewInput {
  reviewType: ReviewType;
  title: string;
  projectId?: number;
  projectName?: string;
  objectType?: string;
  objectId?: number;
  objectNo?: string;
  objectVersionNo?: string;
  participants?: string;
  attachmentDocumentId?: number;
  attachmentVersionNo?: string;
  approvalInstanceId?: number;
}

/** 分页查询评审 */
export function pageReviews(query: ReviewQuery): Promise<PageResult<Review>> {
  return http.get<PageResult<Review>>('/pdm/v1/collab/review/page', { ...query });
}

/** 评审详情 */
export function getReview(id: number): Promise<Review> {
  return http.get<Review>(`/pdm/v1/collab/review/${id}`);
}

/** 新建评审 */
export function createReview(input: ReviewInput): Promise<Review> {
  return http.post<Review>('/pdm/v1/collab/review', input);
}

/** 更新草稿评审 */
export function updateReview(id: number, input: ReviewInput): Promise<Review> {
  return http.put<Review>(`/pdm/v1/collab/review/${id}`, input);
}

/** 关闭评审并写入结论 */
export function closeReview(
  id: number,
  conclusion: ReviewConclusion,
  conclusionComment?: string,
): Promise<Review> {
  return http.post<Review>(`/pdm/v1/collab/review/${id}/close`, { conclusion, conclusionComment });
}

/** 删除草稿评审 */
export function deleteReview(id: number): Promise<void> {
  return http.del<void>(`/pdm/v1/collab/review/${id}`);
}

/** 预览类型 */
export type PreviewType = '2D' | '3D';

/** 预览状态 */
export type PreviewStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'UNSUPPORTED';

/** 前端查看器提示 */
export type PreviewViewer = 'PDF' | 'IMAGE' | 'STL' | 'GLTF';

/** 在线预览任务 */
export interface PreviewJob {
  id: number;
  documentId: number;
  versionId: number;
  versionNo: string;
  previewType: PreviewType;
  sourceFileName?: string;
  sourceContentType?: string;
  targetFormat?: string;
  viewerHint?: PreviewViewer;
  status: PreviewStatus;
  retryCount: number;
  lastError?: string;
  artifactFileId?: number;
  artifactSize?: number;
  startedAt?: string;
  finishedAt?: string;
  createTime?: string;
}

/** 分页查询预览任务 */
export function pagePreviewJobs(query: {
  current?: number;
  size?: number;
  documentId?: number;
  versionId?: number;
}): Promise<PageResult<PreviewJob>> {
  return http.get<PageResult<PreviewJob>>('/pdm/v1/collab/preview/page', { ...query });
}

/** 创建（或复用）预览任务 */
export function createPreview(
  documentId: number,
  versionId: number,
  previewType: PreviewType,
): Promise<PreviewJob> {
  return http.post<PreviewJob>(`/pdm/v1/collab/preview/${documentId}/versions/${versionId}`, {
    previewType,
  });
}

/** 重试预览任务 */
export function retryPreview(jobId: number): Promise<PreviewJob> {
  return http.post<PreviewJob>(`/pdm/v1/collab/preview/${jobId}/retry`);
}

/** 读取预览内容（inline 字节流） */
export function downloadPreviewContent(jobId: number): Promise<{ blob: Blob; filename: string }> {
  return download(`/pdm/v1/collab/preview/${jobId}/content`);
}
