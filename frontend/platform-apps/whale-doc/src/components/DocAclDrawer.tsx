// 页面级权限配置：受限开关 + ACL 授权列表（M2-4）

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Divider,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Typography,
} from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { aclApi } from '../api/acl';
import type { DocAclRole } from '../api/types';

const { Text } = Typography;

/** 页面 ACL 编辑行 */
interface AclRow {
  userId: number;
  aclRole: DocAclRole;
}

/** 组件属性 */
interface DocAclDrawerProps {
  pageId: number;
  pageTitle: string;
  open: boolean;
  onClose: () => void;
  /** 保存成功后触发页面刷新 */
  onUpdated: () => void;
}

export default function DocAclDrawer({ pageId, pageTitle, open, onClose, onUpdated }: DocAclDrawerProps) {
  const { message } = App.useApp();
  const [restricted, setRestricted] = useState(false);
  const [rows, setRows] = useState<AclRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const acl = await aclApi.get(pageId);
      setRestricted(acl.restricted);
      setRows(acl.items.map((item) => ({ userId: Number(item.userId), aclRole: item.aclRole })));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [pageId, message]);

  useEffect(() => {
    if (open) {
      void load();
    }
  }, [open, load]);

  const updateRow = (index: number, patch: Partial<AclRow>) => {
    setRows((prev) => prev.map((row, current) => (current === index ? { ...row, ...patch } : row)));
  };

  const removeRow = (index: number) => {
    setRows((prev) => prev.filter((_, current) => current !== index));
  };

  const save = async () => {
    const valid = rows.filter((row) => Number.isFinite(row.userId) && row.userId > 0);
    const unique = new Map<number, DocAclRole>();
    valid.forEach((row) => unique.set(row.userId, row.aclRole));
    if (restricted && unique.size === 0) {
      message.warning('启用页面级权限后至少需要授权 1 名用户');
      return;
    }
    setSaving(true);
    try {
      await aclApi.update(pageId, {
        restricted,
        items: Array.from(unique.entries()).map(([userId, aclRole]) => ({ userId, aclRole })),
      });
      message.success('页面权限已更新');
      onUpdated();
      onClose();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={`页面权限 · ${pageTitle}`}
      open={open}
      confirmLoading={saving}
      onOk={() => void save()}
      onCancel={onClose}
      okText="保存"
      width={600}
      destroyOnHidden
    >
      <Spin spinning={loading}>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space>
            <Switch checked={restricted} onChange={setRestricted} />
            <Text>{restricted ? '仅授权用户与空间管理员可见' : '继承空间可见性'}</Text>
          </Space>
          <Text type="secondary">
            空间 OWNER / ADMIN 始终可读写；READ 仅可阅读与评论，EDIT 可编辑正文。
          </Text>
          <Divider style={{ margin: '4px 0' }} />
          <List
            dataSource={rows}
            locale={{ emptyText: '尚未授权任何用户' }}
            renderItem={(row, index) => (
              <List.Item
                actions={[
                  <Button
                    key="remove"
                    type="link"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={() => removeRow(index)}
                  />,
                ]}
              >
                <Space>
                  <InputNumber
                    min={1}
                    precision={0}
                    placeholder="用户ID"
                    value={row.userId}
                    onChange={(value) => updateRow(index, { userId: Number(value ?? 0) })}
                  />
                  <Select<DocAclRole>
                    style={{ width: 120 }}
                    value={row.aclRole}
                    options={[
                      { value: 'READ', label: '只读' },
                      { value: 'EDIT', label: '可编辑' },
                    ]}
                    onChange={(value) => updateRow(index, { aclRole: value })}
                  />
                </Space>
              </List.Item>
            )}
          />
          <Button
            type="dashed"
            block
            icon={<PlusOutlined />}
            disabled={rows.length >= 100}
            onClick={() => setRows((prev) => [...prev, { userId: 0, aclRole: 'READ' }])}
          >
            添加授权用户
          </Button>
        </Space>
      </Spin>
    </Modal>
  );
}
