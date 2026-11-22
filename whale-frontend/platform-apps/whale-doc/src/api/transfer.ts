// 文档导入导出 API（M3）

import { requestBlob, upload } from './client';
import type { DocImportResult } from './types';

/** 导出格式 */
export type ExportFormat = 'md' | 'html';

/** 文档导入导出接口 */
export const transferApi = {
  /** 导出正文为 Blob（Markdown / HTML） */
  exportPage: (pageId: number, format: ExportFormat): Promise<Blob> =>
    requestBlob(`/doc/v1/page/${pageId}/export?format=${format}`),

  /** 上传 Markdown / HTML 覆盖正文 */
  importPage: (pageId: number, file: File, format?: ExportFormat): Promise<DocImportResult> => {
    const form = new FormData();
    form.append('file', file);
    if (format) {
      form.append('format', format);
    }
    return upload<DocImportResult>(`/doc/v1/page/${pageId}/import`, form);
  },
};

/** 触发浏览器下载 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
