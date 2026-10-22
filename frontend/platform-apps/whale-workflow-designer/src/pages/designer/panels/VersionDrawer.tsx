// 版本管理抽屉：版本快照列表（发布 / 迁移入口）+ 迁移运行中实例弹窗。
// 发布后回拉定义详情，交由页面 applyDefinition 同步画布与元信息。

import {
  Alert,
  App,
  Button,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import {
  workflowApi,
  type DefinitionMigrationResult,
  type Id,
  type ProcessDefinition,
  type ProcessDefinitionVersion,
} from '../../../api/workflow';
import { toMessage } from '../errors';

interface VersionDrawerProps {
  open: boolean;
  definitionId: Id | null;
  onClose: () => void;
  /** 发布成功后回传最新定义（由页面重置画布） */
  onPublished: (definition: ProcessDefinition) => void;
}

export default function VersionDrawer({
  open,
  definitionId,
  onClose,
  onPublished,
}: VersionDrawerProps) {
  const { message } = App.useApp();
  const [versionLoading, setVersionLoading] = useState(false);
  const [versions, setVersions] = useState<ProcessDefinitionVersion[]>([]);
  const [publishingVersion, setPublishingVersion] = useState<number | null>(null);

  const [migrateOpen, setMigrateOpen] = useState(false);
  const [migrateVersion, setMigrateVersion] = useState<number | null>(null);
  const [migrateIds, setMigrateIds] = useState('');
  const [migrating, setMigrating] = useState(false);
  const [migration, setMigration] = useState<DefinitionMigrationResult | null>(null);

  /** 拉取版本快照列表 */
  const loadVersions = useCallback(async () => {
    if (!definitionId) {
      return;
    }
    setVersionLoading(true);
    try {
      const result = await workflowApi.definitionVersions(definitionId);
      setVersions(result);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setVersionLoading(false);
    }
  }, [definitionId, message]);

  // 打开抽屉时重置迁移状态并刷新版本列表
  useEffect(() => {
    if (!open) {
      return;
    }
    setMigration(null);
    setMigrateOpen(false);
    void loadVersions();
  }, [open, loadVersions]);

  /** 发布指定版本，并把最新定义交回页面同步画布 */
  const handlePublish = async (version: number) => {
    if (!definitionId) {
      return;
    }
    setPublishingVersion(version);
    try {
      await workflowApi.definitionPublish(definitionId, { version });
      message.success(`已发布版本 v${version}`);
      await loadVersions();
      const definition = await workflowApi.definitionDetail(definitionId);
      onPublished(definition);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setPublishingVersion(null);
    }
  };

  /** 打开迁移弹窗并预填目标版本 */
  const openMigrate = (version: number) => {
    setMigration(null);
    setMigrateVersion(version);
    setMigrateIds('');
    setMigrateOpen(true);
  };

  /** 迁移运行中实例到目标版本（实例 ID 保持字符串，避免雪花 ID 丢精度） */
  const handleMigrate = async () => {
    if (!definitionId) {
      return;
    }
    if (migrateVersion === null) {
      message.warning('请填写目标版本号');
      return;
    }
    const ids = migrateIds
      .split(/[\s,，]+/)
      .map((item) => item.trim())
      .filter(Boolean);
    setMigrating(true);
    try {
      const result = await workflowApi.definitionMigrate(definitionId, {
        targetVersion: migrateVersion,
        ...(ids.length > 0 ? { instanceIds: ids } : {}),
      });
      setMigration(result);
      message.success(
        `迁移完成：成功 ${result.migrated} / 跳过 ${result.skipped} / 失败 ${result.failed}`,
      );
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setMigrating(false);
    }
  };

  const versionColumns: TableProps<ProcessDefinitionVersion>['columns'] = [
    {
      title: '版本',
      dataIndex: 'version',
      width: 90,
      render: (version: number) => <Tag color="blue">v{version}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'publishStatus',
      width: 100,
      render: (status?: number | null) =>
        status === 1 ? <Tag color="green">已发布</Tag> : <Tag>草稿</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      render: (createTime?: string) => createTime ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            loading={publishingVersion === record.version}
            onClick={() => void handlePublish(record.version)}
          >
            发布
          </Button>
          <Button type="link" size="small" onClick={() => openMigrate(record.version)}>
            迁移
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Drawer
        title="版本管理"
        width={640}
        open={open}
        onClose={onClose}
        extra={
          <Button size="small" onClick={() => void loadVersions()}>
            刷新
          </Button>
        }
      >
        <Spin spinning={versionLoading}>
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Typography.Text type="secondary">
              发布后新实例按该版本发起；迁移把运行中实例切换到目标版本的结构。
            </Typography.Text>
            <Table
              size="small"
              rowKey="id"
              dataSource={versions}
              columns={versionColumns}
              pagination={false}
              locale={{ emptyText: '暂无版本快照' }}
            />
          </Space>
        </Spin>
      </Drawer>

      <Modal
        title="迁移运行中实例"
        open={migrateOpen}
        okText="开始迁移"
        confirmLoading={migrating}
        onOk={() => void handleMigrate()}
        onCancel={() => setMigrateOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <InputNumber
            min={1}
            precision={0}
            value={migrateVersion}
            onChange={(value) => setMigrateVersion(typeof value === 'number' ? value : null)}
          />
          <Input.TextArea
            rows={4}
            placeholder="实例 ID（可选，逗号/换行分隔；留空=全部运行中实例）"
            value={migrateIds}
            onChange={(event) => setMigrateIds(event.target.value)}
          />
          <Typography.Text type="secondary">
            实例 ID 按字符串提交，避免雪花 ID 精度丢失。
          </Typography.Text>
          {migration ? (
            <Alert
              type={migration.failed > 0 ? 'warning' : 'success'}
              showIcon
              message={`迁移完成：成功 ${migration.migrated} / 跳过 ${migration.skipped} / 失败 ${migration.failed}`}
              description={
                migration.failures.length > 0 ? (
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {migration.failures.map((failure, index) => (
                      <li key={`${failure}-${index}`}>{failure}</li>
                    ))}
                  </ul>
                ) : undefined
              }
            />
          ) : null}
        </Space>
      </Modal>
    </>
  );
}
