// 文件上传：音频与封面上传到 whale-file（POST /file/upload → file_id）
// 上传为 multipart，不能手动设置 Content-Type（需保留浏览器生成的 boundary）。

import { getToken, upload, type Result } from './client';

/** 上传接口返回的文件标识 */
export interface FileUploadVO {
  id: number;
  fileName?: string;
  originalName?: string;
  bucket?: string;
  objectKey?: string;
  fileSize?: number;
  contentType?: string;
}

/** 上传文件，返回文件 ID（失败时由调用方提示） */
export async function uploadFile(file: File, bucket?: string): Promise<FileUploadVO> {
  const formData = new FormData();
  formData.append('file', file);
  if (bucket) {
    formData.append('bucket', bucket);
  }
  const result = await upload<FileUploadVO>('/file/upload', formData);
  if (!result?.id) {
    throw new Error('上传成功但未返回文件标识');
  }
  return result;
}

/** 读取音频时长（毫秒）；浏览器无法解析时返回 undefined */
export function readAudioDuration(file: File): Promise<number | undefined> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    audio.preload = 'metadata';
    audio.onloadedmetadata = () => {
      const duration = Number.isFinite(audio.duration)
        ? Math.round(audio.duration * 1000)
        : undefined;
      URL.revokeObjectURL(url);
      resolve(duration);
    };
    audio.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(undefined);
    };
    audio.src = url;
  });
}

/** 带鉴权的文件下载响应（M1 仅用于封面预览，失败不阻塞主流程） */
export async function fetchFileBlob(fileId: number): Promise<Blob> {
  const token = getToken();
  const response = await fetch(`/file/${fileId}/download`, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    throw new Error(`文件下载失败（HTTP ${response.status}）`);
  }
  const payloadType = response.headers.get('content-type') ?? '';
  if (payloadType.includes('application/json')) {
    const payload = (await response.json()) as Result<unknown>;
    throw new Error(payload.message ?? '文件下载失败');
  }
  return response.blob();
}
