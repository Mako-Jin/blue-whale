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
      message.success('文件已上传');
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
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const columns: TableProps<FileInfoVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '原始文件名',
      dataIndex: 'originalName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '存储名',
      dataIndex: 'fileName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '桶',
      dataIndex: 'bucket',
      width: 120,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 110,
      render: (value: number | undefined) => formatSize(value),
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
      width: 160,
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
    <ProTable<FileInfoVO, FileQuery>
      ref={tableRef}
      rowKey="id"
      title="文件列表"
      columns={columns}
      request={pageFiles}
      params={{ originalName }}
      onError={(error) => showError(error, '文件列表加载失败')}
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
  );
}
