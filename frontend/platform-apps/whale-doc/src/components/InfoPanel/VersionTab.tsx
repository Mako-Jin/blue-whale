// 版本历史页签：快照列表、预览、手动保存与恢复

import { useCallback, useEffect, useState } from 'react';
import { App, Button, Empty, Input, List, Modal, Pagination, Space, Spin, Tag, Typography } from 'antd';
import { HistoryOutlined } from '@ant-design/icons';
import { hasPermission } from '../../api/client';
import { versionApi } from '../../api/version';
import type { DocPage, DocVersion, DocVersionKind } from '../../api/types';
import { formatTime } from '../../utils/docContent';
import VersionPreview from '../VersionPreview';

const { Text } = Typography;

/** 版本类型文案 */
const KIND_LABEL: Record<DocVersionKind, string> = {
  MANUAL: '手动',
  PUBLISH: '发布',
  AUTO: '自动',
  RESTORE: '恢复',
};

/** 版本历史页签 */
export default function VersionTab({
  page,
  onReload,
  onTreeChange,
}: {
  page: DocPage;
  onReload: () => Promise<void>;
  onTreeChange: () => void;
}) {
  const { message, modal } = App.useApp();
  const [versions, setVersions] = useState<DocVersion[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<DocVersion | null>(null);
  const [manualOpen, setManualOpen] = useState(false);
  const [summary, setSummary] = useState('');
  const [working, setWorking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await versionApi.page(page.id, current, 10);
      setVersions(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, current, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openPreview = async (version: DocVersion) => {
    try {
      setPreview(await versionApi.detail(page.id, version.revisionNo));
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const saveManual = async () => {
    setWorking(true);
    try {
      await versionApi.manual(page.id, summary.trim() || undefined);
      message.success('版本快照已保存');
      setManualOpen(false);
      setSummary('');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setWorking(false);
    }
  };

  const restore = (version: DocVersion) => {
    modal.confirm({
      title: `恢复到 r${version.revisionNo}？`,
      content: '将以该版本正文生成新的 revision，当前正文会被覆盖。',
      okText: '恢复',
      onOk: async () => {
        await versionApi.restore(page.id, version.revisionNo);
        message.success('已恢复历史版本');
        await onReload();
        onTreeChange();
        await load();
      },
    });
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button icon={<HistoryOutlined />} onClick={() => setManualOpen(true)} disabled={!page.canEdit}>
          保存当前版本
        </Button>
        <Text type="secondary">共 {total} 个快照</Text>
      </Space>
      <Spin spinning={loading}>
        <List
          dataSource={versions}
          locale={{ emptyText: <Empty description="暂无版本快照" /> }}
          renderItem={(version) => (
            <List.Item
              actions={[
                <Button key="preview" type="link" size="small" onClick={() => void openPreview(version)}>
                  预览
                </Button>,
                <Button
                  key="restore"
                  type="link"
                  size="small"
                  disabled={!page.canEdit || !hasPermission('sys:doc:version:restore')}
                  onClick={() => restore(version)}
                >
                  恢复
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Text strong>r{version.revisionNo}</Text>
                    <Tag>{KIND_LABEL[version.versionKind]}</Tag>
                    <Text>{version.title}</Text>
                  </Space>
                }
                description={
                  <Space direction="vertical" size={2}>
                    <Text type="secondary">编辑人 {version.editorId}</Text>
                    <Text type="secondary">{formatTime(version.createTime)}</Text>
                    {version.changeSummary && <Text>{version.changeSummary}</Text>}
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Spin>
      {total > 10 && (
        <div style={{ textAlign: 'right', marginTop: 12 }}>
          <Pagination current={current} pageSize={10} total={total} showSizeChanger={false}
            onChange={setCurrent} size="small" />
        </div>
      )}

      <Modal
        title={`版本 r${preview?.revisionNo ?? ''} 预览`}
        open={Boolean(preview)}
        onCancel={() => setPreview(null)}
        footer={null}
        width={760}
        destroyOnHidden
      >
        {preview && <VersionPreview content={preview.contentJson} />}
      </Modal>

      <Modal
        title="保存版本快照"
        open={manualOpen}
        confirmLoading={working}
        onOk={() => void saveManual()}
        onCancel={() => setManualOpen(false)}
      >
        <Input.TextArea
          rows={3}
          maxLength={200}
          showCount
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          placeholder="本次快照的变更说明（可选）"
        />
      </Modal>
    </div>
  );
}
