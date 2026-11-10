// 附件层：上传走 whale-file（multipart），预览/下载带 Bearer 拉取字节流
//
// @bluewhale/http 会给所有 JSON 请求设置 Content-Type，multipart 必须走原生 fetch。

import { createLogger } from '@bluewhale/whale-common-tools';
import { ApiError, downloadBlob } from './client';
import { getToken } from './token';

const logger = createLogger('whale-im › file');

/** 上传成功后的文件元信息（与 FileUploadVO 对齐） */
export interface UploadedFile {
  id: string;
  fileName: string;
  originalName: string;
  bucket: string;
  objectKey: string;
  fileSize: number;
  contentType: string;
  md5: string;
}

interface UploadResult {
  code: number;
  message: string;
  data: UploadedFile;
}

/** 上传文件到 whale-file；bucket 固定为 im */
export async function uploadFile(file: File, bucket = 'im'): Promise<UploadedFile> {
  const form = new FormData();
  form.append('file', file);
  form.append('bucket', bucket);
  const token = getToken();
  const headers = new Headers();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch('/file/upload', { method: 'POST', headers, body: form });
  const payload = (await response.json()) as UploadResult;
  if (!response.ok || payload.code !== 0) {
    logger.warn('附件上传失败', payload?.message);
    throw new ApiError(payload?.code ?? response.status, payload?.message ?? '附件上传失败');
  }
  return { ...payload.data, id: String(payload.data.id) };
}

/** 拉取图片预览字节流（携带鉴权头，避免 <img src> 无法带 token） */
export async function previewBlob(fileId: string): Promise<Blob> {
  const result = await downloadBlob(`/file/${fileId}/preview`);
  return result.blob;
}

/** 下载附件并返回文件名 */
export async function downloadFile(fileId: string): Promise<{ blob: Blob; filename: string }> {
  return downloadBlob(`/file/${fileId}/download`);
}
