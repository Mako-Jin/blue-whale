// 页面附件 API

import { http, requestBlob, upload } from './client';
import type { DocAttachment, DocAttachmentUsage } from './types';

/** 附件接口 */
export const attachmentApi = {
  list: (pageId: number) => http.get<DocAttachment[]>(`/doc/v1/page/${pageId}/attachment`),
  upload: (pageId: number, file: File, usageType: DocAttachmentUsage = 'ATTACHMENT') => {
    const form = new FormData();
    form.append('file', file);
    form.append('usageType', usageType);
    return upload<DocAttachment>(`/doc/v1/page/${pageId}/attachment`, form);
  },
  remove: (pageId: number, attachmentId: number) =>
    http.del<void>(`/doc/v1/page/${pageId}/attachment/${attachmentId}`),
  download: (contentUrl: string) => requestBlob(contentUrl),
};
