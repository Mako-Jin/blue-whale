// 文件 API（经网关 → whale-file /file）
// 上传/下载走原生 fetch：上传不能手动设置 Content-Type（需保留浏览器生成的 boundary），
// 下载需要读取二进制 blob，统一 HTTP 客户端只解析 JSON 信封，故不复用。

import { del, get, type PageResult, type Result } from './http';
import { getToken } from './token';

export interface FileInfoVO {
  id: number;
  fileName?: string;
  originalName?: string;
  bucket?: string;
  objectKey?: string;
  fileSize?: number;
  contentType?: string;
  md5?: string;
  uploaderId?: number;
  createTime?: string;
}

export interface FilePageParams {
  originalName?: string;
  bucket?: string;
  current: number;
  size: number;
}

/** 上传接口返回的文件标识 */
interface FileUploadVO {
  id: number;
  fileName?: string;
  originalName?: string;
  bucket?: string;
  objectKey?: string;
  fileSize?: number;
  contentType?: string;
}

export function pageFiles(params: FilePageParams): Promise<PageResult<FileInfoVO>> {
  return get<PageResult<FileInfoVO>>('/file/page', { ...params });
}

export function deleteFile(id: number): Promise<void> {
  return del<void>(`/file/${id}`);
}

/** 构造仅含 Authorization 的请求头（token 缺失时不加） */
function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 上传文件：multipart/form-data，由浏览器自动补齐 boundary */
export async function uploadFile(file: File, bucket?: string): Promise<FileUploadVO> {
  const formData = new FormData();
  formData.append('file', file);
  if (bucket) {
    formData.append('bucket', bucket);
  }

  const response = await fetch('/file/upload', {
    method: 'POST',
    headers: authHeaders(),
    body: formData,
  });
  const payload = (await response.json().catch(() => null)) as Result<FileUploadVO> | null;
  if (!response.ok || !payload || payload.code !== 0) {
    throw new Error(payload?.message ?? `上传失败（HTTP ${response.status}）`);
  }
  return payload.data;
}

/** 下载文件：取 blob 后用临时 <a download> 触发保存 */
export async function downloadFile(id: number, filename?: string): Promise<void> {
  const response = await fetch(`/file/${id}/download`, {
    method: 'GET',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(`下载失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename ?? `file-${id}`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
