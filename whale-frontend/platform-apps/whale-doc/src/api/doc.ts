// 文档服务元信息：GET /doc/meta

import { http } from './client';

/** 文档服务运行元信息（对齐后端 DocMetaVO） */
export interface DocMeta {
  service: string;
  tenantId: number;
  userId: number | string | null;
  collabEnabled: boolean;
  approvalEnabled: boolean;
  contentMaxBytes: number;
  contentTextMaxBytes: number;
  attachmentMaxBytes: number;
  maxDepth: number;
}

export const docApi = {
  /** 读取服务运行参数与功能开关 */
  meta: () => http.get<DocMeta>('/doc/v1/meta'),
};
