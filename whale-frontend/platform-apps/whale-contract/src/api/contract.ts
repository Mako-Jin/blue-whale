// Whale Contract 接口定义：合同主流程 / 相对方 / 版本 / 流程记录

import { http, type PageResult } from './client';

/** 合同状态 */
export type ContractStatus =
  | 'DRAFT'
  | 'APPROVING'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'SIGNING'
  | 'SIGNED'
  | 'EFFECTIVE'
  | 'ARCHIVED'
  | 'TERMINATED'
  | 'VOID';

/** 合同相对方 */
export interface ContractParty {
  id?: string;
  contractId?: string;
  partyType: string;
  name: string;
  creditCode?: string | null;
  contact?: string | null;
  phone?: string | null;
  signerUserId?: string | null;
  signerName?: string | null;
}

/** 合同版本 */
export interface ContractVersion {
  id: string;
  contractId: string;
  versionNo: number;
  fileId?: string | null;
  sha256?: string | null;
  changeNote?: string | null;
  status: string;
  createTime?: string;
}

/** 合同流程记录 */
export interface ContractFlow {
  id: string;
  contractId: string;
  versionId?: string | null;
  processInstanceId?: string | null;
  definitionId?: string | null;
  stage: string;
  status: string;
  createTime?: string;
  updateTime?: string;
}

/** 合同 */
export interface Contract {
  id: string;
  code: string;
  title: string;
  category: string;
  amount?: number | null;
  currency?: string | null;
  ownerId?: string | null;
  deptId?: string | null;
  status: ContractStatus;
  currentVersion: number;
  fileId?: string | null;
  effectiveDate?: string | null;
  expireDate?: string | null;
  daysToExpire?: number | null;
  archiveItemId?: string | null;
  bizType?: string | null;
  bizId?: string | null;
  remark?: string | null;
  createTime?: string;
  updateTime?: string;
  parties?: ContractParty[];
  versions?: ContractVersion[];
  flows?: ContractFlow[];
}

/** 合同查询条件 */
export interface ContractQuery {
  current?: number;
  size?: number;
  code?: string;
  title?: string;
  category?: string;
  status?: string;
  expiringSoon?: boolean;
}

/** 合同保存入参 */
export interface ContractSaveInput {
  title: string;
  category: string;
  amount?: number;
  currency?: string;
  ownerId?: number;
  deptId?: number;
  fileId?: number;
  effectiveDate?: string;
  expireDate?: string;
  bizType?: string;
  bizId?: string;
  remark?: string;
  parties?: ContractParty[];
}

/** 合同接口 */
export const contractApi = {
  page: (query: ContractQuery) => http.get<PageResult<Contract>>('/contract/v1/page', { ...query }),
  detail: (id: string) => http.get<Contract>(`/contract/v1/${id}`),
  create: (input: ContractSaveInput) => http.post<Contract>('/contract/v1', input),
  update: (id: string, input: ContractSaveInput) => http.put<Contract>(`/contract/v1/${id}`, input),
  remove: (id: string) => http.del<void>(`/contract/v1/${id}`),
  submit: (id: string) => http.post<Contract>(`/contract/v1/${id}/submit`),
  withdraw: (id: string) => http.post<Contract>(`/contract/v1/${id}/withdraw`),
  terminate: (id: string) => http.post<Contract>(`/contract/v1/${id}/terminate`),
  effective: (id: string) => http.post<Contract>(`/contract/v1/${id}/effective`),
  archive: (id: string) => http.post<Contract>(`/contract/v1/${id}/archive`),
  versions: (id: string) => http.get<ContractVersion[]>(`/contract/v1/${id}/versions`),
  createVersion: (id: string, input: { fileId?: number; sha256?: string; changeNote?: string }) =>
    http.post<ContractVersion>(`/contract/v1/${id}/versions`, input),
};
