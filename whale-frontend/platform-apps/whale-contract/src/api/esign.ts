// Whale Contract 电子签章接口：印章 / 用章授权 / 签署任务 / 证据链 / 验签

import { http, requestBlob, type PageResult } from './client';

/** 印章 */
export interface Seal {
  id: string;
  name: string;
  sealType?: string | null;
  imageFileId?: string | null;
  keeperUserId?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
  status: string;
  createTime?: string;
  updateTime?: string;
}

/** 用章授权 */
export interface SealGrant {
  id: string;
  sealId: string;
  granteeUserId: string;
  contractCategory?: string | null;
  maxAmount?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  status: string;
  grantedBy?: string | null;
  createTime?: string;
}

/** 签署任务 */
export interface SignTask {
  id: string;
  contractId: string;
  contractCode?: string | null;
  contractTitle?: string | null;
  contractAmount?: number | null;
  contractCategory?: string | null;
  versionId?: string | null;
  seqNo?: number | null;
  signerUserId?: string | null;
  signerParty?: string | null;
  signerName?: string | null;
  provider?: string | null;
  providerTaskId?: string | null;
  signUrl?: string | null;
  workflowTaskId?: string | null;
  status: string;
  signedAt?: string | null;
  signedFileId?: string | null;
  refuseReason?: string | null;
  createTime?: string;
}

/** 签署记录 */
export interface SignRecord {
  id: string;
  contractId: string;
  signTaskId: string;
  signerUserId?: string | null;
  sealId?: string | null;
  certSerial?: string | null;
  signedAt?: string | null;
  originalSha256?: string | null;
  signedSha256?: string | null;
  algorithm?: string | null;
  evidenceNo?: string | null;
  chainHash?: string | null;
  clientIp?: string | null;
  realNameStatus?: string | null;
  realNameMethod?: string | null;
  realNameEvidenceNo?: string | null;
  willingnessMethod?: string | null;
  willingnessEvidenceNo?: string | null;
  willingnessAt?: string | null;
}

/** 证据链 */
export interface Evidence {
  contractId: string;
  contractCode?: string | null;
  contractStatus?: string | null;
  tasks: SignTask[];
  records: SignRecord[];
  chainValid?: boolean | null;
}

/** 验签结果 */
export interface VerifyResult {
  contractId: string;
  hashMatch: boolean;
  chainValid: boolean;
  signatureValid: boolean;
  algorithm?: string | null;
  signedFileId?: string | null;
  expectedSha256?: string | null;
  actualSha256?: string | null;
  details?: string[];
}

/** 签署结果 */
export interface SignResult {
  taskId: string;
  recordId: string;
  evidenceNo: string;
  originalSha256: string;
  signedSha256: string;
  algorithm: string;
  signedFileId: string;
  stampedPages?: number | null;
  taskStatus: string;
  contractStatus: string;
}

/** 印章保存入参 */
export interface SealSaveInput {
  name: string;
  sealType?: string;
  imageFileId?: number;
  keeperUserId?: number;
  validFrom?: string;
  validTo?: string;
  status?: string;
}

/** 用章授权入参 */
export interface SealGrantInput {
  granteeUserId: number;
  contractCategory?: string;
  maxAmount?: number;
  validFrom?: string;
  validTo?: string;
}

/** 发起签署入参 */
export interface SignStartInput {
  provider?: string;
  tasks?: Array<{
    signerUserId?: number;
    signerParty?: string;
    signerName?: string;
    workflowTaskId?: number;
  }>;
}

/** 本地签署入参 */
export interface SignExecuteInput {
  sealId: number;
  page?: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  rotation?: number;
  crossPage?: boolean;
  confirmWillingness: boolean;
}

/** 印章与授权接口 */
export const sealApi = {
  page: (query: { current?: number; size?: number; keyword?: string; status?: string }) =>
    http.get<PageResult<Seal>>('/contract/v1/seal/page', query),
  create: (input: SealSaveInput) => http.post<Seal>('/contract/v1/seal', input),
  update: (id: string, input: SealSaveInput) => http.put<Seal>(`/contract/v1/seal/${id}`, input),
  remove: (id: string) => http.del<void>(`/contract/v1/seal/${id}`),
  grant: (id: string, input: SealGrantInput) =>
    http.post<SealGrant>(`/contract/v1/seal/${id}/grant`, input),
  grants: (id: string) => http.get<SealGrant[]>(`/contract/v1/seal/${id}/grants`),
  preview: (id: string) => requestBlob(`/contract/v1/seal/${id}/preview`),
  download: (id: string) => requestBlob(`/contract/v1/seal/${id}/download`),
};

/** 签署与证据链接口 */
export const signApi = {
  start: (id: string, input?: SignStartInput) =>
    http.post<SignTask[]>(`/contract/v1/${id}/sign/start`, input ?? {}),
  tasks: (id: string) => http.get<SignTask[]>(`/contract/v1/${id}/sign/tasks`),
  pending: () => http.get<SignTask[]>('/contract/v1/sign/pending'),
  sign: (taskId: string, input: SignExecuteInput) =>
    http.post<SignResult>(`/contract/v1/sign-task/${taskId}/sign`, input),
  refuse: (taskId: string, reason: string) =>
    http.post<void>(`/contract/v1/sign-task/${taskId}/refuse`, { reason }),
  evidence: (id: string) => http.get<Evidence>(`/contract/v1/${id}/evidence`),
  verify: (id: string) => http.post<VerifyResult>(`/contract/v1/${id}/verify`),
  void: (id: string) => http.post<void>(`/contract/v1/${id}/void`),
};
