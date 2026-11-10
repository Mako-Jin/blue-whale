// 文件上传接口：复用平台 /file/upload（multipart），返回文件元数据
import { http } from './client';

/** 上传结果（对齐后端 FileUploadVO） */
export interface UploadedFile {
  id: number;
  fileName: string;
  originalName?: string;
  fileSize?: number;
  contentType?: string;
}

/** 上传单个文件，返回文件ID供业务登记附件引用 */
export function uploadOaFile(file: File): Promise<UploadedFile> {
  const formData = new FormData();
  formData.append('file', file);
  return http.upload<UploadedFile>('/file/upload', formData);
}
