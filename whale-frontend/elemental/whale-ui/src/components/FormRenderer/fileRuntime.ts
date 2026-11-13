// FormRenderer 文件运行时：上传/下载的默认 fetch 实现（multipart 上传、字节流下载触发浏览器保存）

import type { FormRendererProps, WhaleUploadedFile } from './types';

/** 文件上传/下载运行时配置 */
export interface FileRuntime {
  authToken?: string;
  apiBaseUrl?: string;
  uploadFile?: FormRendererProps['uploadFile'];
  downloadFile?: FormRendererProps['downloadFile'];
}

/** 构造鉴权请求头（令牌为空则不带头） */
function buildAuthHeaders(token?: string): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 默认上传：POST {apiBaseUrl}/file/upload（multipart 字段名 file） */
export async function defaultUpload(file: File, runtime: FileRuntime): Promise<WhaleUploadedFile> {
  const body = new FormData();
  body.append('file', file);
  const response = await fetch(`${runtime.apiBaseUrl ?? ''}/file/v1/upload`, {
    method: 'POST',
    headers: buildAuthHeaders(runtime.authToken),
    body,
  });
  if (!response.ok) {
    throw new Error(`上传失败（HTTP ${response.status}）`);
  }
  const result = (await response.json()) as {
    code?: number;
    message?: string;
    data?: { id?: string | number; originalName?: string; fileSize?: number };
  };
  if (result.code !== 0 || !result.data || result.data.id === undefined) {
    throw new Error(result.message || '上传失败');
  }
  return {
    id: String(result.data.id),
    name: result.data.originalName ?? file.name,
    size: typeof result.data.fileSize === 'number' ? result.data.fileSize : file.size,
  };
}

/** 默认下载：GET {apiBaseUrl}/file/{id}/download，按响应字节流触发浏览器保存 */
export async function defaultDownload(
  file: WhaleUploadedFile,
  runtime: FileRuntime,
): Promise<void> {
  const response = await fetch(`${runtime.apiBaseUrl ?? ''}/file/v1/${file.id}/download`, {
    headers: buildAuthHeaders(runtime.authToken),
  });
  if (!response.ok) {
    throw new Error(`下载失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.name ?? `file-${file.id}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
