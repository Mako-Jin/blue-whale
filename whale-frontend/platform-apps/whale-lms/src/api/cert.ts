// LMS 证书域接口封装（M4-1：证书模板 / 发证 / 撤销 / 换发 / 我的证书）。
//
// 发证口径完全由模板决定：模板声明「按考试通过发证」时，前端必须带作答记录 ID，
// 服务端会去核实成绩是否及格；调用方无法通过改 sourceType 绕开校验。
// 证书状态里 EXPIRED 是展示期派生态（由有效期算出），不是落库状态。

import { http } from './client';

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 发证依据 / 证书来源 */
export type CertSource = 'MANUAL' | 'COURSE' | 'EXAM' | 'TRAINING';

/** 证书展示状态（EXPIRED 为派生值） */
export type CertStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'RENEWED';

/** 证书模板 */
export interface CertTemplateItem {
  id: number;
  templateCode: string;
  name: string;
  category: string;
  issueBasis: CertSource;
  validMonths: number;
  longTerm: boolean;
  certPrefix: string;
  content?: string;
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 证书 */
export interface CertificateItem {
  id: number;
  certNo: string;
  templateId: number;
  templateName: string;
  category: string;
  userId: number;
  username?: string;
  sourceType: CertSource;
  sourceId: number;
  sourceTitle?: string;
  issueYear: number;
  seqNo: number;
  issuedAt: string;
  validFrom: string;
  validTo?: string;
  longTerm: boolean;
  status: CertStatus;
  expired: boolean;
  revokedAt?: string;
  revokedBy?: number;
  revokeReason?: string;
  renewFromId: number;
  remark?: string;
}

/** 模板分页 */
export function pageCertTemplates(params: {
  keyword?: string;
  category?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<CertTemplateItem>>('/lms/v1/cert/template/page', { ...params });
}

/** 模板详情 */
export function certTemplateDetail(id: number | string) {
  return http.get<CertTemplateItem>(`/lms/v1/cert/template/${id}`);
}

/** 新建模板 */
export function createCertTemplate(body: {
  templateCode: string;
  name: string;
  category?: string;
  issueBasis?: CertSource;
  validMonths?: number;
  certPrefix?: string;
  content?: string;
  remark?: string;
}) {
  return http.post<number>('/lms/v1/cert/template', body);
}

/** 编辑模板 */
export function updateCertTemplate(
  id: number,
  body: {
    templateCode: string;
    name: string;
    category?: string;
    issueBasis?: CertSource;
    validMonths?: number;
    certPrefix?: string;
    content?: string;
    remark?: string;
  },
) {
  return http.put<void>(`/lms/v1/cert/template/${id}`, body);
}

/** 删除模板（已被证书引用时后端拒绝） */
export function deleteCertTemplate(id: number) {
  return http.del<void>(`/lms/v1/cert/template/${id}`);
}

/** 启用 / 停用模板 */
export function changeCertTemplateStatus(id: number, enabled: boolean) {
  return http.put<void>(`/lms/v1/cert/template/${id}/status?enabled=${enabled}`);
}

/** 证书分页（管理端） */
export function pageCertificates(params: {
  keyword?: string;
  templateId?: number;
  userId?: number;
  sourceType?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<CertificateItem>>('/lms/v1/certificate/page', { ...params });
}

/** 证书详情 */
export function certificateDetail(id: number | string) {
  return http.get<CertificateItem>(`/lms/v1/certificate/${id}`);
}

/** 发证 */
export function issueCertificate(body: {
  templateId: number;
  userId: number;
  username?: string;
  sourceType?: CertSource;
  sourceId?: number;
  sourceTitle?: string;
  remark?: string;
}) {
  return http.post<number>('/lms/v1/certificate/issue', body);
}

/** 撤销证书 */
export function revokeCertificate(id: number, reason: string) {
  return http.put<void>(`/lms/v1/certificate/${id}/revoke`, { reason });
}

/** 换发证书 */
export function renewCertificate(id: number, remark?: string) {
  return http.post<number>(`/lms/v1/certificate/${id}/renew`, { remark });
}

/** 我的证书 */
export function myCertificates(params: { status?: string; current?: number; size?: number }) {
  return http.get<PageData<CertificateItem>>('/lms/v1/certificate/mine', { ...params });
}
