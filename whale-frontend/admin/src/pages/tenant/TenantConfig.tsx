// 租户配置页（Wave 6.19）：权限中心通用配置的租户默认值层
//
// 读取优先级：用户配置 > 租户配置 > 消费方默认（用户层在基座「通知偏好」页自助维护）。
// auth 侧只存取不解释语义：键必须以两段以上的小写前缀命名（如 notify.twin.alert），
// 值必须是合法 JSON 且不超过 8KB，具体含义由消费方（message / twin / 前端）自行解释。
//
// 权限码：sys:tenant:config:list（页面可见）/ sys:tenant:config:edit（保存按钮）

import { Alert, App, Button, Form, Input, Modal, Space, Table, Tooltip, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePermission } from '@bluewhale/whale-ui';
import { listTenantConfig, upsertTenantConfig, type TenantConfigEntry } from '../../api/tenant';

/** 值长度上限（与 auth ConfigKeyValidator.MAX_VALUE_LENGTH 对齐） */
const MAX_VALUE_LENGTH = 8192;

/** 配置键形态（与 auth CONFIG_KEY_PATTERN 对齐：至少两段，小写字母开头） */
const KEY_PATTERN = /^[a-z][a-z0-9-]*(\.[a-z0-9][a-z0-9-]*)+$/;

/** 编辑表单值 */
interface ConfigFormValues {
  configKey: string;
  configValue: string;
}

/** 常用配置键提示（当前仅通知域有消费方；新增域在此登记后页面给出建议项） */
const KEY_HINTS: ReadonlyArray<{ key: string; label: string }> = [
  { key: 'notify.twin.alert', label: '孪生告警站内信/邮件偏好（message 解析）' },
];

/** 解析并格式化 JSON，非法时返回 null（仅用于展示美化） */
function prettyJson(raw: string): string | null {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return null;
  }
}

export default function TenantConfigList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const canEdit = has('sys:tenant:config:edit');

  const [rows, setRows] = useState<TenantConfigEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [prefix, setPrefix] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [form] = Form.useForm<ConfigFormValues>();

  /** 拉取配置列表（按前缀过滤） */
  const load = useCallback(
    async (keyword?: string) => {
      setLoading(true);
      try {
        const list = await listTenantConfig(keyword || undefined);
        setRows(list ?? []);
      } catch (error) {
        message.error(error instanceof Error ? error.message : '租户配置加载失败');
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = useCallback(() => {
    setEditingKey(null);
    form.setFieldsValue({ configKey: '', configValue: '{\n  \n}' });
    setFormOpen(true);
  }, [form]);

  const openEdit = useCallback(
    (row: TenantConfigEntry) => {
      setEditingKey(row.configKey);
      form.setFieldsValue({
        configKey: row.configKey,
        configValue: prettyJson(row.configValue) ?? row.configValue,
      });
      setFormOpen(true);
    },
    [form],
  );

  const handleSave = useCallback(async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await upsertTenantConfig({
        configKey: values.configKey.trim(),
        configValue: values.configValue,
      });
      message.success('租户配置已保存');
      setFormOpen(false);
      await load(prefix);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }, [form, load, message, prefix]);

  const columns: TableProps<TenantConfigEntry>['columns'] = useMemo(
    () => [
      {
        title: '配置键',
        dataIndex: 'configKey',
        width: 260,
        render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
      },
      {
        title: '配置值（JSON）',
        dataIndex: 'configValue',
        render: (value: string) => {
          const pretty = prettyJson(value) ?? value;
          const oneLine = pretty.replace(/\s+/g, ' ');
          const truncated = oneLine.length > 120 ? `${oneLine.slice(0, 120)}…` : oneLine;
          return (
            <Tooltip title={<pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{pretty}</pre>}>
              <span>{truncated}</span>
            </Tooltip>
          );
        },
      },
      {
        title: '操作',
        key: 'action',
        width: 100,
        render: (_, row) =>
          canEdit ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : (
            '-'
          ),
      },
    ],
    [canEdit, openEdit],
  );

  return (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="这里是本租户的默认值层"
        description={
          <span>
            生效顺序为「用户配置 &gt; 租户配置 &gt;
            消费方默认」：用户在自己的「通知偏好」页设置过则以
            用户配置为准，否则回落到本页。键名至少两段并以业务域开头（如
            notify.twin.alert），值必须是 合法 JSON（≤8KB）——auth 只负责存取，语义由消费方解释。
          </span>
        }
      />

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          allowClear
          placeholder="按键前缀过滤，如 notify."
          style={{ width: 320 }}
          onSearch={(value) => {
            setPrefix(value.trim());
            void load(value.trim());
          }}
        />
        {canEdit ? (
          <Button type="primary" onClick={openCreate}>
            新增配置
          </Button>
        ) : null}
        <Button onClick={() => void load(prefix)}>刷新</Button>
      </Space>

      <Table<TenantConfigEntry>
        rowKey="configKey"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '该租户尚未配置任何默认值（消费方将按内置默认放行）' }}
      />

      <Modal
        title={editingKey ? `编辑配置：${editingKey}` : '新增租户配置'}
        open={formOpen}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setFormOpen(false)}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="configKey"
            label="配置键"
            rules={[
              { required: true, message: '请输入配置键' },
              {
                pattern: KEY_PATTERN,
                message: '需两段以上小写标识，以点分隔（如 notify.twin.alert）',
              },
            ]}
            extra={
              editingKey
                ? '配置键创建后不可修改；如需改名请新增一条并让消费方改用新键'
                : `常用键：${KEY_HINTS.map((item) => item.key).join(' / ')}`
            }
          >
            <Input placeholder="notify.twin.alert" disabled={Boolean(editingKey)} maxLength={128} />
          </Form.Item>
          <Form.Item
            name="configValue"
            label="配置值（JSON）"
            rules={[
              { required: true, message: '请输入配置值' },
              {
                validator: (_rule, value: string) => {
                  if (!value) {
                    return Promise.resolve();
                  }
                  if (value.length > MAX_VALUE_LENGTH) {
                    return Promise.reject(new Error(`配置值不能超过 ${MAX_VALUE_LENGTH} 字节`));
                  }
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('必须是合法 JSON'));
                  }
                },
              },
            ]}
          >
            <Input.TextArea
              rows={8}
              spellCheck={false}
              placeholder={'{\n  "channels": ["INAPP"]\n}'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
