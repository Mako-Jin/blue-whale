// 页面级权限 API（M2-4）

import { http } from './client';
import type { DocAclItem, DocAclRole, DocPageAcl } from './types';

/** 页面 ACL 更新参数 */
export interface DocPageAclPayload {
  restricted: boolean;
  items: Array<{ userId: number; aclRole: DocAclRole }>;
}

/** 页面级权限接口 */
export const aclApi = {
  get: (pageId: number) => http.get<DocPageAcl>(`/doc/v1/page/${pageId}/acl`),
  update: (pageId: number, payload: DocPageAclPayload) =>
    http.put<DocPageAcl>(`/doc/v1/page/${pageId}/acl`, {
      restricted: payload.restricted,
      items: payload.items as DocAclItem[],
    }),
};
