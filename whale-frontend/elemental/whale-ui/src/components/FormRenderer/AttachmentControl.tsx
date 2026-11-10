// FormRenderer 附件控件：上传 / 回显（文件名、大小）/ 下载 / 删除，值仅保存文件 ID 数组

import { useRef, useState } from 'react';
import { Button, Space, Typography } from 'antd';
import { defaultDownload, defaultUpload, type FileRuntime } from './fileRuntime';
import type { WhaleFormFieldConfig, WhaleUploadedFile } from './types';

/** 文件大小展示（B / KB / MB） */
function formatSize(size?: number): string {
  if (size === undefined || size <= 0) {
    return '';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 附件值 → 文件 ID 字符串数组 */
function toFileIds(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => String(item)) : [];
}

export function AttachmentControl({
  value,
  onChange,
  field,
  disabled,
  runtime,
}: {
  value?: unknown;
  onChange?: (next: string[]) => void;
  field: WhaleFormFieldConfig;
  disabled: boolean;
  runtime: FileRuntime;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [meta, setMeta] = useState<Record<string, WhaleUploadedFile>>({});
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ids = toFileIds(value);
  const maxCount = field.maxCount !== undefined && field.maxCount > 0 ? field.maxCount : 0;
  const limit = field.multiple === false ? 1 : maxCount;
  const limitReached = limit > 0 && ids.length >= limit;

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) {
      return;
    }
    const selected = Array.from(files);
    const allowed = limit > 0 ? selected.slice(0, Math.max(limit - ids.length, 0)) : selected;
    if (allowed.length < selected.length) {
      setError(`最多允许上传 ${limit} 个文件`);
    } else {
      setError(null);
    }
    if (allowed.length === 0) {
      return;
    }
    setUploading(true);
    try {
      const uploaded: WhaleUploadedFile[] = [];
      for (const file of allowed) {
        // 顺序上传，保持用户选择顺序
        uploaded.push(
          runtime.uploadFile ? await runtime.uploadFile(file) : await defaultUpload(file, runtime),
        );
      }
      setMeta((previous) => {
        const next = { ...previous };
        uploaded.forEach((item) => {
          next[item.id] = item;
        });
        return next;
      });
      onChange?.([...ids, ...uploaded.map((item) => item.id)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (id: string) => {
    const file: WhaleUploadedFile = { id, name: meta[id]?.name };
    try {
      if (runtime.downloadFile) {
        await runtime.downloadFile(file);
        return;
      }
      await defaultDownload(file, runtime);
    } catch (e) {
      setError(e instanceof Error ? e.message : '下载失败');
    }
  };

  return (
    <div>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        {ids.length === 0 ? (
          <Typography.Text type="secondary">暂无附件</Typography.Text>
        ) : (
          ids.map((id) => (
            <Space key={id} size={8} wrap>
              <Typography.Text>{meta[id]?.name ?? `文件 #${id}`}</Typography.Text>
              {meta[id]?.size ? (
                <Typography.Text type="secondary">{formatSize(meta[id]?.size)}</Typography.Text>
              ) : null}
              <Button
                size="small"
                type="link"
                disabled={disabled}
                onClick={() => void handleDownload(id)}
              >
                下载
              </Button>
              <Button
                size="small"
                type="link"
                danger
                disabled={disabled}
                onClick={() => onChange?.(ids.filter((item) => item !== id))}
              >
                删除
              </Button>
            </Space>
          ))
        )}
        <Space wrap>
          <Button
            size="small"
            disabled={disabled || uploading || limitReached}
            loading={uploading}
            onClick={() => inputRef.current?.click()}
          >
            上传文件
          </Button>
          {limit > 0 ? <Typography.Text type="secondary">最多 {limit} 个</Typography.Text> : null}
          {field.accept ? (
            <Typography.Text type="secondary">支持类型：{field.accept}</Typography.Text>
          ) : null}
        </Space>
        {error ? <Typography.Text type="danger">{error}</Typography.Text> : null}
      </Space>
      <input
        ref={inputRef}
        type="file"
        accept={field.accept}
        multiple={field.multiple !== false}
        style={{ display: 'none' }}
        onChange={(event) => {
          const files = event.target.files;
          void handleFiles(files);
          event.target.value = '';
        }}
      />
    </div>
  );
}
