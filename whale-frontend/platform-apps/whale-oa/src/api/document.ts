// OA 公文与档案 API 层：模板、文号规则、发文、收文、附件与归档
import { downloadBlob, http } from './client';
import type { PageResult } from './client';

/** 公文单据类型 */
export type OaDocType = 'OUTGOING' | 'INCOMING';

/** 发文状态 */
export type OaOutgoingStatus =
  'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SIGNED' | 'ISSUED' | 'ARCHIVED';

/** 收文状态 */
export type OaIncomingStatus = 'REGISTERED' | 'REVIEWED' | 'FORWARDED' | 'COMPLETED' | 'ARCHIVED';

/** 归档映射状态 */
export type OaArchiveStatus = 'PENDING' | 'ARCHIVED' | 'FAILED';

/** 公文附件 */
export interface OaDocAttachment {
  id: number;
  fileId: number;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  sortNo?: number;
}

/** 归档映射 */
export interface OaDocArchiveRef {
  id: number;
  docType: OaDocType;
  docId: number;
  docNo?: string;
  docTitle?: string;
  sourceType?: string;
  sourceId?: string;
  archiveItemId?: string;
  status: OaArchiveStatus;
  errorMessage?: string;
  pushedAt?: string;
  createTime?: string;
}

/** 公文模板 */
export interface OaDocTemplate {
  id: number;
  code: string;
  name: string;
  docType: OaDocType;
  version: number;
  status: 'DRAFT' | 'RELEASED' | 'DISABLED';
  headerText?: string;
  footerText?: string;
  bodyTemplate?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 文号规则 */
export interface OaDocNoRule {
  id: number;
  code: string;
  name: string;
  docType: OaDocType;
  prefix?: string;
  periodFormat: 'YEAR' | 'MONTH' | 'NONE';
  serialLength: number;
  separator?: string;
  enabled: number;
  remark?: string;
  createTime?: string;
}

/** 发文 */
export interface OaDocOutgoing {
  id: number;
  docNo: string;
  title: string;
  docType: string;
  securityLevel: number;
  urgency: number;
  templateId?: number;
  templateName?: string;
  drafterId: number;
  drafterName?: string;
  deptId?: number;
  mainRecipients?: string;
  copyRecipients?: string;
  content?: string;
  signer?: string;
  signDate?: string;
  issuedAt?: string;
  status: OaOutgoingStatus;
  approvalInstanceId?: number;
  archiveItemId?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  attachments?: OaDocAttachment[];
  archiveRef?: OaDocArchiveRef;
}

/** 收文 */
export interface OaDocIncoming {
  id: number;
  incomingNo: string;
  sourceUnit: string;
  sourceNo?: string;
  title: string;
  securityLevel: number;
  urgency: number;
  receivedDate?: string;
  dueDate?: string;
  assigneeId?: number;
  assigneeName?: string;
  instruction?: string;
  handledAt?: string;
  status: OaIncomingStatus;
  archiveItemId?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  attachments?: OaDocAttachment[];
  archiveRef?: OaDocArchiveRef;
}

/** 公文分页查询 */
export interface OaDocQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  docType?: string;
  securityLevel?: number;
}

/** 附件入参 */
export interface OaDocAttachmentInput {
  fileId: number;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
}

/** 模板保存入参 */
export interface OaDocTemplateInput {
  code: string;
  name: string;
  docType: OaDocType;
  version?: number;
  status?: string;
  headerText?: string;
  footerText?: string;
  bodyTemplate?: string;
  remark?: string;
}

/** 文号规则保存入参 */
export interface OaDocNoRuleInput {
  code: string;
  name: string;
  docType: OaDocType;
  prefix?: string;
  periodFormat?: 'YEAR' | 'MONTH' | 'NONE';
  serialLength?: number;
  separator?: string;
  enabled?: number;
  remark?: string;
}

/** 发文保存入参 */
export interface OaDocOutgoingInput {
  title: string;
  docType?: string;
  securityLevel?: number;
  urgency?: number;
  templateId?: number;
  deptId?: number;
  mainRecipients?: string;
  copyRecipients?: string;
  content?: string;
  remark?: string;
  attachments?: OaDocAttachmentInput[];
}

/** 收文保存入参 */
export interface OaDocIncomingInput {
  sourceUnit: string;
  sourceNo?: string;
  title: string;
  securityLevel?: number;
  urgency?: number;
  receivedDate?: string;
  dueDate?: string;
  remark?: string;
  attachments?: OaDocAttachmentInput[];
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

/** 分页查询公文模板 */
export function pageDocumentTemplates(params: OaDocQuery = {}): Promise<PageResult<OaDocTemplate>> {
  return http.get<PageResult<OaDocTemplate>>(`/oa/v1/document/templates/page${buildQuery(params)}`);
}

/** 查询全部公文模板 */
export function listDocumentTemplates(docType?: OaDocType): Promise<OaDocTemplate[]> {
  return http.get<OaDocTemplate[]>(`/oa/v1/document/templates/all${buildQuery({ docType })}`);
}

/** 新增公文模板 */
export function createDocumentTemplate(input: OaDocTemplateInput): Promise<OaDocTemplate> {
  return http.post<OaDocTemplate>('/oa/v1/document/templates', input);
}

/** 更新公文模板 */
export function updateDocumentTemplate(
  id: number,
  input: OaDocTemplateInput,
): Promise<OaDocTemplate> {
  return http.put<OaDocTemplate>(`/oa/v1/document/templates/${id}`, input);
}

/** 删除公文模板 */
export function deleteDocumentTemplate(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/document/templates/${id}`);
}

/** 查询文号规则 */
export function listDocNoRules(): Promise<OaDocNoRule[]> {
  return http.get<OaDocNoRule[]>('/oa/v1/document/no-rules');
}

/** 新增文号规则 */
export function createDocNoRule(input: OaDocNoRuleInput): Promise<OaDocNoRule> {
  return http.post<OaDocNoRule>('/oa/v1/document/no-rules', input);
}

/** 更新文号规则 */
export function updateDocNoRule(id: number, input: OaDocNoRuleInput): Promise<OaDocNoRule> {
  return http.put<OaDocNoRule>(`/oa/v1/document/no-rules/${id}`, input);
}

/** 分页查询发文 */
export function pageOutgoingDocs(params: OaDocQuery = {}): Promise<PageResult<OaDocOutgoing>> {
  return http.get<PageResult<OaDocOutgoing>>(`/oa/v1/document/outgoing/page${buildQuery(params)}`);
}

/** 发文详情 */
export function getOutgoingDoc(id: number): Promise<OaDocOutgoing> {
  return http.get<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}`);
}

/** 拟稿 */
export function createOutgoingDoc(input: OaDocOutgoingInput): Promise<OaDocOutgoing> {
  return http.post<OaDocOutgoing>('/oa/v1/document/outgoing', input);
}

/** 编辑发文 */
export function updateOutgoingDoc(id: number, input: OaDocOutgoingInput): Promise<OaDocOutgoing> {
  return http.put<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}`, input);
}

/** 删除发文 */
export function deleteOutgoingDoc(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/document/outgoing/${id}`);
}

/** 提交审核 */
export function submitOutgoingDoc(id: number): Promise<OaDocOutgoing> {
  return http.post<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}/submit`);
}

/** 审核发文 */
export function auditOutgoingDoc(id: number, approved: boolean): Promise<OaDocOutgoing> {
  return http.post<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}/audit`, { approved });
}

/** 签发发文 */
export function signOutgoingDoc(
  id: number,
  input: { signer?: string; signDate?: string } = {},
): Promise<OaDocOutgoing> {
  return http.post<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}/sign`, input);
}

/** 下发发文 */
export function issueOutgoingDoc(id: number): Promise<OaDocOutgoing> {
  return http.post<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}/issue`);
}

/** 归档发文 */
export function archiveOutgoingDoc(id: number): Promise<OaDocOutgoing> {
  return http.post<OaDocOutgoing>(`/oa/v1/document/outgoing/${id}/archive`);
}

/** 分页查询收文 */
export function pageIncomingDocs(params: OaDocQuery = {}): Promise<PageResult<OaDocIncoming>> {
  return http.get<PageResult<OaDocIncoming>>(`/oa/v1/document/incoming/page${buildQuery(params)}`);
}

/** 收文详情 */
export function getIncomingDoc(id: number): Promise<OaDocIncoming> {
  return http.get<OaDocIncoming>(`/oa/v1/document/incoming/${id}`);
}

/** 收文登记 */
export function createIncomingDoc(input: OaDocIncomingInput): Promise<OaDocIncoming> {
  return http.post<OaDocIncoming>('/oa/v1/document/incoming', input);
}

/** 编辑收文 */
export function updateIncomingDoc(id: number, input: OaDocIncomingInput): Promise<OaDocIncoming> {
  return http.put<OaDocIncoming>(`/oa/v1/document/incoming/${id}`, input);
}

/** 删除收文 */
export function deleteIncomingDoc(id: number): Promise<void> {
  return http.del<void>(`/oa/v1/document/incoming/${id}`);
}

/** 批阅收文 */
export function reviewIncomingDoc(
  id: number,
  input: { instruction: string; assigneeId?: number; assigneeName?: string },
): Promise<OaDocIncoming> {
  return http.post<OaDocIncoming>(`/oa/v1/document/incoming/${id}/review`, input);
}

/** 转发收文 */
export function forwardIncomingDoc(
  id: number,
  input: { assigneeId?: number; assigneeName?: string; remark?: string } = {},
): Promise<OaDocIncoming> {
  return http.post<OaDocIncoming>(`/oa/v1/document/incoming/${id}/forward`, input);
}

/** 办结收文 */
export function completeIncomingDoc(id: number): Promise<OaDocIncoming> {
  return http.post<OaDocIncoming>(`/oa/v1/document/incoming/${id}/complete`);
}

/** 归档收文 */
export function archiveIncomingDoc(id: number): Promise<OaDocIncoming> {
  return http.post<OaDocIncoming>(`/oa/v1/document/incoming/${id}/archive`);
}

/** 分页查询归档映射 */
export function pageArchiveRefs(params: OaDocQuery = {}): Promise<PageResult<OaDocArchiveRef>> {
  return http.get<PageResult<OaDocArchiveRef>>(
    `/oa/v1/document/archive/refs/page${buildQuery(params)}`,
  );
}

/** 重试归档 */
export function retryArchiveRef(id: number): Promise<OaDocArchiveRef> {
  return http.post<OaDocArchiveRef>(`/oa/v1/document/archive/refs/${id}/retry`);
}

/** 下载公文附件 */
export async function downloadDocAttachment(
  docType: OaDocType,
  docId: number,
  attachmentId: number,
  fileName: string,
): Promise<void> {
  const result = await downloadBlob(
    `/oa/v1/document/attachments/${docType}/${docId}/${attachmentId}/download`,
  );
  const url = URL.createObjectURL(result.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = result.filename || fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
