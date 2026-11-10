// 文件上传：视频走 whale-file 分片上传（5MB/片，支持续传），封面走普通上传
// 上传为 multipart，不能手动设置 Content-Type（需保留浏览器生成的 boundary）。

import { http, upload } from './client';

/** 上传接口返回的文件标识 */
export interface FileUploadVO {
  id: number;
  fileName?: string;
  originalName?: string;
  bucket?: string;
  objectKey?: string;
  fileSize?: number;
  contentType?: string;
  md5?: string;
}

/** 分片上传初始化结果 */
export interface ChunkUploadInitVO {
  uploadId: string;
  chunkSize: number;
  uploadedIndexes: number[];
}

/** 视频元信息（取自浏览器本地解析，随元数据一并提交） */
export interface VideoMetadata {
  durationMs: number;
  width?: number;
  height?: number;
}

/** 上传文件（普通上传，适合封面等小文件） */
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

/**
 * 分片上传视频文件；按服务端下发的分片大小逐片上传并回报进度。
 *
 * @param file 视频文件
 * @param onProgress 已上传字节数回调（用于进度条）
 * @returns 上传结果（含文件 ID）
 */
export async function uploadVideoFile(
  file: File,
  onProgress?: (uploadedBytes: number, totalBytes: number) => void,
): Promise<FileUploadVO> {
  const init = await http.post<ChunkUploadInitVO>('/file/upload/chunk/init', {
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    size: file.size,
  });
  if (!init?.uploadId || !init.chunkSize) {
    throw new Error('分片上传初始化失败');
  }
  const chunkSize = init.chunkSize;
  const uploaded = new Set<number>(init.uploadedIndexes ?? []);
  const totalChunks = Math.ceil(file.size / chunkSize);
  onProgress?.(uploaded.size * chunkSize, file.size);
  for (let index = 0; index < totalChunks; index += 1) {
    if (uploaded.has(index)) {
      continue;
    }
    const start = index * chunkSize;
    const slice = file.slice(start, Math.min(start + chunkSize, file.size));
    const formData = new FormData();
    formData.append('file', slice, file.name);
    formData.append('uploadId', init.uploadId);
    formData.append('index', String(index));
    await upload<number[]>('/file/upload/chunk/part', formData);
    uploaded.add(index);
    onProgress?.(Math.min((index + 1) * chunkSize, file.size), file.size);
  }
  const result = await http.post<FileUploadVO>('/file/upload/chunk/complete', {
    uploadId: init.uploadId,
    fileName: file.name,
    size: file.size,
  });
  if (!result?.id) {
    throw new Error('分片上传完成但未返回文件标识');
  }
  return result;
}

/** 读取视频时长与分辨率；浏览器无法解析时抛出错误 */
export function readVideoMetadata(file: File): Promise<VideoMetadata> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.onloadedmetadata = () => {
      const duration = Number.isFinite(video.duration) ? Math.round(video.duration * 1000) : 0;
      const width = video.videoWidth || undefined;
      const height = video.videoHeight || undefined;
      URL.revokeObjectURL(url);
      if (duration <= 0) {
        reject(new Error('无法读取视频时长，请确认文件为浏览器可播放的 MP4 / WebM'));
        return;
      }
      resolve({ durationMs: duration, width, height });
    };
    video.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('无法解析视频文件，请确认格式为浏览器可播放的 MP4 / WebM'));
    };
    video.src = url;
  });
}
