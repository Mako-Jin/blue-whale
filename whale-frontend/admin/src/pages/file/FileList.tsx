// 文件管理页 - 分页查询 / 上传 / 下载 / 删除
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import { App, Button, Input, Popconfirm, Space, Tag, Upload, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import {
  AuthButton,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import { deleteFile, downloadFile, pageFiles, uploadFile, type FileInfoVO } from '../../api/file';
import { useI18n } from '../../i18n';

type FileQuery = { originalName?: string };

/** 文件大小格式化为 B / KB / MB */
function formatSize(size?: number): string {
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

export default function FileList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [originalName, setOriginalName] = useState<string | undefined>(undefined);
  const [uploading, setUploading] = useState(false);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      await uploadFile(file);
      message.success(t('file.msg.uploaded'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('file.msg.uploadFailed'));
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (row: FileInfoVO) => {
    try {
      await downloadFile(row.id, row.originalName ?? row.fileName);
    } catch (error) {
      showError(error, t('file.msg.downloadFailed'));
    }
  };

  const handleDelete = async (row: FileInfoVO) => {
    try {
      await deleteFile(row.id);
      message.success(t('file.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('file.msg.deleteFailed'));
    }
  };

  const columns: TableProps<FileInfoVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('file.col.originalName'),
      dataIndex: 'originalName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('file.col.storedName'),
      dataIndex: 'fileName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('file.col.bucket'),
      dataIndex: 'bucket',
      width: 120,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('file.col.size'),
      dataIndex: 'fileSize',
      width: 110,
      render: (value: number | undefined) => formatSize(value),
    },
    {
      title: t('file.col.type'),
      dataIndex: 'contentType',
      width: 180,
      render: (value: string | undefined) => (value ? <Tag>{value}</Tag> : '-'),
    },
    {
      title: t('file.col.uploadTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('file.col.action'),
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4}>
          <AuthButton
            perm="sys:file:download"
            type="link"
            size="small"
            onClick={() => void handleDownload(row)}
          >
            {t('file.action.download')}
          </AuthButton>
          {has('sys:file:delete') && (
            <Popconfirm
              title={t('file.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('file.action.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <ProTable<FileInfoVO, FileQuery>
      ref={tableRef}
      rowKey="id"
      title={t('file.list.title')}
      columns={columns}
      request={pageFiles}
      params={{ originalName }}
      onError={(error) => showError(error, t('file.msg.listLoadFailed'))}
      search={
        <SearchForm
          items={[
            {
              name: 'originalName',
              label: t('file.search.label'),
              element: <Input allowClear placeholder={t('file.search.placeholder')} />,
            },
          ]}
          onSearch={(values) => setOriginalName((values.originalName as string) || undefined)}
          onReset={() => setOriginalName(undefined)}
        />
      }
      toolbar={
        has('sys:file:upload') && (
          <Upload
            showUploadList={false}
            beforeUpload={(file) => {
              void handleUpload(file);
              // 返回 false 阻止 antd 自动上传，改由 uploadFile 走统一鉴权链路
              return false;
            }}
          >
            <Button type="primary" loading={uploading}>
              {t('file.action.upload')}
            </Button>
          </Upload>
        )
      }
    />
  );
}
