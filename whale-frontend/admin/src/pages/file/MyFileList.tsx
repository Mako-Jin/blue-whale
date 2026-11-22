// 我的文件（个人上传列表）- 个人配额 / 上传 / 下载 / 删除
// 与「文件管理」（FileList）的区别：本页固定 onlyMine=true，管理员也只看本人上传的文件；
// 按钮沿用同一批 sys:file:* 权限码，无权限自动隐藏（AuthButton / usePermission）。

import { Alert, App, Button, Input, Popconfirm, Space, Tag, Upload, type TableProps } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  deleteFile,
  downloadFile,
  getFileQuota,
  pageFiles,
  uploadFile,
  type FileInfoVO,
  type FileQuotaVO,
} from '../../api/file';
import { useI18n } from '../../i18n';

type MyFileQuery = { originalName?: string };

/** 文件大小格式化为 B / KB / MB / GB */
function formatSize(size?: string | number): string {
  if (size === undefined || size === null) {
    return '-';
  }
  const bytes = typeof size === 'string' ? Number(size) : size;
  if (!Number.isFinite(bytes)) {
    return '-';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export default function MyFileList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [originalName, setOriginalName] = useState<string | undefined>(undefined);
  const [uploading, setUploading] = useState(false);
  const [quota, setQuota] = useState<FileQuotaVO | undefined>(undefined);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const quotaText = (value: FileQuotaVO | undefined): string => {
    if (!value) {
      return t('file.quota.loading');
    }
    if (value.unlimited) {
      return t('file.quota.unlimited', { used: formatSize(value.usedBytes) });
    }
    return t('file.quota.used', {
      used: formatSize(value.usedBytes),
      quota: formatSize(value.quotaBytes),
      remaining: formatSize(value.remainingBytes),
    });
  };

  const reloadQuota = () => {
    getFileQuota()
      .then(setQuota)
      .catch((error) => showError(error, t('file.quota.loadFailed')));
  };

  useEffect(() => {
    reloadQuota();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首挂载加载一次
  }, []);

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      await uploadFile(file);
      message.success(t('file.msg.uploaded'));
      reloadQuota();
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
      reloadQuota();
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('file.msg.deleteFailed'));
    }
  };

  const columns: TableProps<FileInfoVO>['columns'] = [
    {
      title: t('file.col.originalName'),
      dataIndex: 'originalName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('file.col.size'),
      dataIndex: 'fileSize',
      width: 110,
      render: (value: string | undefined) => formatSize(value),
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
      width: 140,
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
            <Popconfirm title={t('file.confirm.delete')} onConfirm={() => void handleDelete(row)}>
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
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('file.my.hint')}
        description={quotaText(quota)}
      />
      <ProTable<FileInfoVO, MyFileQuery>
        ref={tableRef}
        rowKey="id"
        title={t('file.my.title')}
        columns={columns}
        request={(params) => pageFiles({ ...params, onlyMine: true })}
        params={{ originalName }}
        onError={(error) => showError(error, t('file.msg.myLoadFailed'))}
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
    </>
  );
}
