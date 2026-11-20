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
import { useI18n } from '../../i18n';

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
  const { t } = useI18n();
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
        message.error(error instanceof Error ? error.message : t('tenant.config.loadFailed'));
      } finally {
        setLoading(false);
      }
    },
    [message, t],
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
      message.success(t('tenant.config.saved'));
      setFormOpen(false);
      await load(prefix);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('tenant.config.saveFailed'));
    } finally {
      setSaving(false);
    }
  }, [form, load, message, prefix, t]);

  const columns: TableProps<TenantConfigEntry>['columns'] = useMemo(
    () => [
      {
        title: t('tenant.config.col.key'),
        dataIndex: 'configKey',
        width: 260,
        render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
      },
      {
        title: t('tenant.config.col.value'),
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
        title: t('tenant.col.action'),
        key: 'action',
        width: 100,
        render: (_, row) =>
          canEdit ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('tenant.action.edit')}
            </Button>
          ) : (
            '-'
          ),
      },
    ],
    [canEdit, openEdit, t],
  );

  return (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={t('tenant.config.alertTitle')}
        description={<span>{t('tenant.config.alertDescription')}</span>}
      />

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          allowClear
          placeholder={t('tenant.config.searchPlaceholder')}
          style={{ width: 320 }}
          onSearch={(value) => {
            setPrefix(value.trim());
            void load(value.trim());
          }}
        />
        {canEdit ? (
          <Button type="primary" onClick={openCreate}>
            {t('tenant.config.create')}
          </Button>
        ) : null}
        <Button onClick={() => void load(prefix)}>{t('tenant.config.refresh')}</Button>
      </Space>

      <Table<TenantConfigEntry>
        rowKey="configKey"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: t('tenant.config.empty') }}
      />

      <Modal
        title={
          editingKey
            ? t('tenant.config.editTitle', { key: editingKey })
            : t('tenant.config.createTitle')
        }
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
            label={t('tenant.config.col.key')}
            rules={[
              { required: true, message: t('tenant.config.keyRequired') },
              {
                pattern: KEY_PATTERN,
                message: t('tenant.config.keyPattern'),
              },
            ]}
            extra={
              editingKey
                ? t('tenant.config.keyImmutable')
                : t('tenant.config.keyHints', {
                    keys: KEY_HINTS.map((item) => item.key).join(' / '),
                  })
            }
          >
            <Input placeholder="notify.twin.alert" disabled={Boolean(editingKey)} maxLength={128} />
          </Form.Item>
          <Form.Item
            name="configValue"
            label={t('tenant.config.col.value')}
            rules={[
              { required: true, message: t('tenant.config.valueRequired') },
              {
                validator: (_rule, value: string) => {
                  if (!value) {
                    return Promise.resolve();
                  }
                  if (value.length > MAX_VALUE_LENGTH) {
                    return Promise.reject(
                      new Error(t('tenant.config.valueTooLong', { max: MAX_VALUE_LENGTH })),
                    );
                  }
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error(t('tenant.config.valueInvalidJson')));
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
