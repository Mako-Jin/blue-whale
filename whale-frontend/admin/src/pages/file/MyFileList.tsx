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

function quotaText(quota: FileQuotaVO | undefined): string {
  if (!quota) {
    return '配额加载中…';
  }
  if (quota.unlimited) {
    return `已用 ${formatSize(quota.usedBytes)} / 不限量`;
  }
  return `已用 ${formatSize(quota.usedBytes)} / ${formatSize(quota.quotaBytes)}，剩余 ${formatSize(quota.remainingBytes)}`;
}

export default function MyFileList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [originalName, setOriginalName] = useState<string | undefined>(undefined);
  const [uploading, setUploading] = useState(false);
  const [quota, setQuota] = useState<FileQuotaVO | undefined>(undefined);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const reloadQuota = () => {
    getFileQuota()
      .then(setQuota)
      .catch((error) => showError(error, '配额加载失败'));
  };

  useEffect(() => {
    reloadQuota();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首挂载加载一次
  }, []);

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      await uploadFile(file);
      message.success('文件已上传');
      reloadQuota();
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (row: FileInfoVO) => {
    try {
      await downloadFile(row.id, row.originalName ?? row.fileName);
    } catch (error) {
      showError(error, '下载失败');
    }
  };

  const handleDelete = async (row: FileInfoVO) => {
    try {
      await deleteFile(row.id);
      message.success('文件已删除');
      reloadQuota();
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const columns: TableProps<FileInfoVO>['columns'] = [
    {
      title: '原始文件名',
      dataIndex: 'originalName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 110,
      render: (value: string | undefined) => formatSize(value),
    },
    {
      title: '类型',
      dataIndex: 'contentType',
      width: 180,
      render: (value: string | undefined) => (value ? <Tag>{value}</Tag> : '-'),
    },
    {
      title: '上传时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
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
            下载
          </AuthButton>
          {has('sys:file:delete') && (
            <Popconfirm title="确认删除该文件？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
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
        message="仅显示当前账号上传的文件；聊天、文档、论坛、商城等系统上传的文件也汇总在此"
        description={quotaText(quota)}
      />
      <ProTable<FileInfoVO, MyFileQuery>
        ref={tableRef}
        rowKey="id"
        title="我的文件"
        columns={columns}
        request={(params) => pageFiles({ ...params, onlyMine: true })}
        params={{ originalName }}
        onError={(error) => showError(error, '我的文件加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'originalName',
                label: '文件名',
                element: <Input allowClear placeholder="原始文件名" />,
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
                上传文件
              </Button>
            </Upload>
          )
        }
      />
    </>
  );
}
