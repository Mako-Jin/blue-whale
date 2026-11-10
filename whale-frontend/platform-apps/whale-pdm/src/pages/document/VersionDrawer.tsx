// 版本抽屉：查看版本历史，发布草稿、回滚历史、下载文件

import { useCallback, useEffect, useState } from 'react';
import { Button, Drawer, Space, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import {
  downloadVersion,
  listVersions,
  releaseVersion,
  rollbackVersion,
  type PdmDocument,
  type PdmDocumentVersion,
} from '../../api/document';
import { hasPermission } from '../../utils/permission';
import { formatFileSize, saveBlob } from './utils';

const VERSION_STATUS: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'blue', label: '草稿' },
  RELEASED: { color: 'green', label: '已发布' },
  OBSOLETE: { color: 'default', label: '已失效' },
};

const SOURCE_LABEL: Record<string, string> = {
  UPLOAD: '上传',
  CHECKIN: '检入',
  ROLLBACK: '回滚',
};

interface VersionDrawerProps {
  open: boolean;
  document: PdmDocument | null;
  onClose: () => void;
  /** 版本或文档状态变化后通知父页面刷新列表 */
  onChanged: () => void;
}

export default function VersionDrawer({ open, document, onClose, onChanged }: VersionDrawerProps) {
  const [versions, setVersions] = useState<PdmDocumentVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [actingId, setActingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!document) {
      setVersions([]);
      return;
    }
    setLoading(true);
    try {
      setVersions(await listVersions(document.id));
    } finally {
      setLoading(false);
    }
  }, [document]);

  useEffect(() => {
    if (open) {
      void load();
    }
  }, [open, load]);

  const handleRelease = async (version: PdmDocumentVersion) => {
    if (!document) {
      return;
    }
    setActingId(version.id);
    try {
      await releaseVersion(document.id, version.id);
      message.success(`版本 ${version.versionNo} 已发布`);
      await load();
      onChanged();
    } finally {
      setActingId(null);
    }
  };

  const handleRollback = async (version: PdmDocumentVersion) => {
    if (!document) {
      return;
    }
    setActingId(version.id);
    try {
      await rollbackVersion(document.id, version.id);
      message.success(`已基于 ${version.versionNo} 生成新草稿`);
      await load();
      onChanged();
    } finally {
      setActingId(null);
    }
  };

  const handleDownload = async (version: PdmDocumentVersion) => {
    if (!document) {
      return;
    }
    setActingId(version.id);
    try {
      const { blob, filename } = await downloadVersion(document.id, version.id);
      saveBlob(blob, filename || version.fileName);
    } finally {
      setActingId(null);
    }
  };

  const columns: TableProps<PdmDocumentVersion>['columns'] = [
    { title: '版本号', dataIndex: 'versionNo', key: 'versionNo', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => {
        const status = VERSION_STATUS[value] ?? { color: 'default', label: value };
        return <Tag color={status.color}>{status.label}</Tag>;
      },
    },
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 90,
      render: (value?: number) => formatFileSize(value),
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 80,
      render: (value: string) => SOURCE_LABEL[value] ?? value,
    },
    { title: '变更说明', dataIndex: 'changeNote', key: 'changeNote', ellipsis: true },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          {hasPermission('sys:pdm:doc:download') ? (
            <Button
              type="link"
              size="small"
              loading={actingId === record.id}
              onClick={() => void handleDownload(record)}
            >
              下载
            </Button>
          ) : null}
          {record.status === 'DRAFT' && hasPermission('sys:pdm:doc:release') ? (
            <Button
              type="link"
              size="small"
              loading={actingId === record.id}
              onClick={() => void handleRelease(record)}
            >
              发布
            </Button>
          ) : null}
          {record.status !== 'DRAFT' && hasPermission('sys:pdm:doc:rollback') ? (
            <Button
              type="link"
              size="small"
              loading={actingId === record.id}
              onClick={() => void handleRollback(record)}
            >
              回滚
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      open={open}
      title={document ? `版本历史 - ${document.docNo}` : '版本历史'}
      width={900}
      onClose={onClose}
    >
      <Table<PdmDocumentVersion>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={versions}
        pagination={false}
        scroll={{ x: 800 }}
      />
    </Drawer>
  );
}
