// PDM 图文档中心通用工具：目录树扁平化、类型文案、文件大小格式化

import { message } from 'antd';
import type { DocFolder, DocType } from '../../api/document';

/** 目录下拉选项 */
export interface FolderOption {
  value: number;
  label: string;
  disabled?: boolean;
}

/** 递归扁平化目录树为下拉选项（label 用目录路径名，便于区分同名目录） */
export function flattenFolders(
  folders: DocFolder[],
  parentLabel = '',
  options: FolderOption[] = [],
): FolderOption[] {
  folders.forEach((folder) => {
    const label = parentLabel ? `${parentLabel} / ${folder.name}` : folder.name;
    options.push({ value: folder.id, label });
    if (folder.children && folder.children.length > 0) {
      flattenFolders(folder.children, label, options);
    }
  });
  return options;
}

/** 递归收集目录及其全部子孙 ID（编辑目录时用于排除自身子树） */
export function collectFolderIds(folder: DocFolder | null): Set<number> {
  const ids = new Set<number>();
  if (!folder) {
    return ids;
  }
  const walk = (node: DocFolder) => {
    ids.add(node.id);
    (node.children ?? []).forEach(walk);
  };
  walk(folder);
  return ids;
}

/** 在目录树中按 ID 查找目录（找不到返回 null） */
export function findFolder(folders: DocFolder[], id?: number): DocFolder | null {
  if (id === undefined || id === null) {
    return null;
  }
  for (const folder of folders) {
    if (folder.id === id) {
      return folder;
    }
    const matched = findFolder(folder.children ?? [], id);
    if (matched) {
      return matched;
    }
  }
  return null;
}

/** 文档类型文案 */
export const DOC_TYPE_LABEL: Record<DocType, string> = {
  DRAWING: '图纸',
  DOC: '技术文档',
  SPEC: '规范',
  REPORT: '报告',
};

/** 文档类型下拉选项 */
export const DOC_TYPE_OPTIONS = (Object.keys(DOC_TYPE_LABEL) as DocType[]).map((value) => ({
  value,
  label: DOC_TYPE_LABEL[value],
}));

/** 文件大小格式化（B / KB / MB） */
export function formatFileSize(size?: number): string {
  if (size === undefined || size === null) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 触发浏览器下载（配合 @bluewhale/http 的 downloadBlob 结果） */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** 统一展示接口错误（ApiError.message 为后端业务提示） */
export function showError(error: unknown): void {
  message.error(error instanceof Error ? error.message : '操作失败，请稍后重试');
}
