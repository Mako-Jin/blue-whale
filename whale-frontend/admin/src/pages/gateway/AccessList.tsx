// 网关访问名单管理页：白名单 / 黑名单双 Tab（黑名单优先于白名单）
// 白名单 = 免鉴权路径；黑名单 = 封禁路径，命中即 403，且不再判断白名单与令牌。
// 操作按钮按权限码渲染（AuthButton / usePermission），无权限自动隐藏；
// 写操作后网关在 60s 内自动拉取生效，页面无需额外通知。

import {
  Alert,
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import { useEffect, useState } from 'react';
import { AuthButton, FormModal, usePermission } from '@bluewhale/whale-ui';
import {
  createGatewayBlacklist,
  createGatewayWhitelist,
  deleteGatewayBlacklist,
  deleteGatewayWhitelist,
  listGatewayBlacklist,
  listGatewayWhitelist,
  updateGatewayBlacklist,
  updateGatewayWhitelist,
  type GatewayAccessRulePayload,
  type GatewayAccessRuleVO,
} from '../../api/gatewayAccess';
import { useI18n } from '../../i18n';

/** 名单类型：whitelist 免鉴权白名单 / blacklist 封禁黑名单 */
type Kind = 'whitelist' | 'blacklist';

interface RuleFormValues {
  pathPattern: string;
  /** 空表示不限方法，提交时统一转成 * */
  httpMethod?: string;
  status?: number;
  sortNo?: number;
  remark?: string;
}

interface KindMeta {
  /** 名单类型文案键 */
  titleKey: 'gateway.whitelist.title' | 'gateway.blacklist.title';
  /** Tab 可见与列表查询权限 */
  listPerm: string;
  addPerm: string;
  editPerm: string;
  deletePerm: string;
  list: (status?: number) => Promise<GatewayAccessRuleVO[]>;
  create: (payload: GatewayAccessRulePayload) => Promise<void>;
  update: (id: string, payload: GatewayAccessRulePayload) => Promise<void>;
  remove: (id: string) => Promise<void>;
}

/** 名单类型 → 文案 / 权限码 / 接口（成对登记，避免运行期拼权限码） */
const KINDS: Record<Kind, KindMeta> = {
  whitelist: {
    titleKey: 'gateway.whitelist.title',
    listPerm: 'sys:gateway:whitelist:list',
    addPerm: 'sys:gateway:whitelist:add',
    editPerm: 'sys:gateway:whitelist:edit',
    deletePerm: 'sys:gateway:whitelist:delete',
    list: listGatewayWhitelist,
    create: createGatewayWhitelist,
    update: updateGatewayWhitelist,
    remove: deleteGatewayWhitelist,
  },
  blacklist: {
    titleKey: 'gateway.blacklist.title',
    listPerm: 'sys:gateway:blacklist:list',
    addPerm: 'sys:gateway:blacklist:add',
    editPerm: 'sys:gateway:blacklist:edit',
    deletePerm: 'sys:gateway:blacklist:delete',
    list: listGatewayBlacklist,
    create: createGatewayBlacklist,
    update: updateGatewayBlacklist,
    remove: deleteGatewayBlacklist,
  },
};

/** 可选 HTTP 方法：* / 空表示不限 */
const HTTP_METHOD_OPTIONS = ['*', 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map(
  (value) => ({ value, label: value }),
);

/** 行数据 → 表单值：* 方法在表单里显示为空（不限） */
function toFormValues(row: GatewayAccessRuleVO): Partial<RuleFormValues> {
  return {
    pathPattern: row.pathPattern,
    httpMethod: row.httpMethod === '*' ? undefined : row.httpMethod,
    status: row.status,
    sortNo: row.sortNo,
    remark: row.remark ?? undefined,
  };
}

function AccessRulePanel({ kind }: { kind: Kind }) {
  const meta = KINDS[kind];
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const title = t(meta.titleKey);

  const statusOptions = [
    { value: 1, label: t('gateway.status.optionEnabled') },
    { value: 0, label: t('gateway.status.optionDisabled') },
  ];

  /** 来源标签：迁移种子与手工维护区分展示 */
  const sourceLabels: Record<string, string> = {
    MIGRATION: t('gateway.source.migration'),
    MANUAL: t('gateway.source.manual'),
  };

  const [rows, setRows] = useState<GatewayAccessRuleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<GatewayAccessRuleVO | null>(null);
  const [editorForm] = Form.useForm<RuleFormValues>();

  // 每次成功操作后重新拉取整份名单（条目数量级很小，无需分页）
  const load = async () => {
    setLoading(true);
    try {
      setRows(await meta.list(statusFilter));
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('gateway.msg.loadFailed', { name: title }),
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const handleDelete = async (row: GatewayAccessRuleVO) => {
    try {
      await meta.remove(row.id);
      message.success(t('gateway.msg.deleted', { name: title }));
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('gateway.msg.deleteFailed'));
    }
  };

  /** 启停只改 status，其余字段原样回传（后端按整体更新处理） */
  const handleToggle = async (row: GatewayAccessRuleVO, next: number) => {
    try {
      await meta.update(row.id, {
        pathPattern: row.pathPattern,
        httpMethod: row.httpMethod,
        status: next,
        sortNo: row.sortNo,
        remark: row.remark ?? undefined,
      });
      message.success(t(next === 1 ? 'gateway.msg.enabled' : 'gateway.msg.disabled'));
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('gateway.msg.toggleFailed'));
    }
  };

  const handleSubmit = async (values: RuleFormValues) => {
    const payload: GatewayAccessRulePayload = {
      pathPattern: values.pathPattern.trim(),
      httpMethod: values.httpMethod || '*',
      status: values.status ?? 1,
      sortNo: values.sortNo ?? 0,
      remark: values.remark?.trim() || undefined,
    };
    try {
      if (editRecord) {
        await meta.update(editRecord.id, payload);
        message.success(t('gateway.msg.updated', { name: title }));
      } else {
        await meta.create(payload);
        message.success(t('gateway.msg.created', { name: title }));
      }
      setEditorOpen(false);
      void load();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : t(editRecord ? 'gateway.msg.updateFailed' : 'gateway.msg.createFailed'),
      );
      throw error;
    }
  };

  const columns: TableProps<GatewayAccessRuleVO>['columns'] = [
    {
      title: t('gateway.col.path'),
      dataIndex: 'pathPattern',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('gateway.col.method'),
      dataIndex: 'httpMethod',
      width: 100,
      render: (value: string | undefined) => <Tag>{value || '*'}</Tag>,
    },
    {
      title: t('gateway.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has(meta.editPerm)}
          checkedChildren={t('gateway.status.enabled')}
          unCheckedChildren={t('gateway.status.disabled')}
          onChange={(checked) => void handleToggle(row, checked ? 1 : 0)}
        />
      ),
    },
    { title: t('gateway.col.sortNo'), dataIndex: 'sortNo', width: 80 },
    {
      title: t('gateway.col.source'),
      dataIndex: 'source',
      width: 100,
      render: (value: string | undefined) => (value ? (sourceLabels[value] ?? value) : '-'),
    },
    {
      title: t('gateway.col.remark'),
      dataIndex: 'remark',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('gateway.col.updateTime'),
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('gateway.col.action'),
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4}>
          <AuthButton
            perm={meta.editPerm}
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            {t('gateway.btn.edit')}
          </AuthButton>
          {has(meta.deletePerm) && (
            <Popconfirm
              title={t('gateway.confirm.delete', { name: title })}
              description={t('gateway.confirm.deleteDescription')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('gateway.btn.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      {kind === 'blacklist' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={t('gateway.alert.blacklistTitle')}
          description={t('gateway.alert.blacklistDescription')}
        />
      )}
      <Table<GatewayAccessRuleVO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        title={() => (
          <Space wrap>
            <span>{t('gateway.list.title', { name: title })}</span>
            <AuthButton
              perm={meta.addPerm}
              type="primary"
              size="small"
              onClick={() => {
                setEditRecord(null);
                setEditorOpen(true);
              }}
            >
              {t('gateway.btn.create', { name: title })}
            </AuthButton>
            <Select<number>
              allowClear
              size="small"
              style={{ width: 150 }}
              placeholder={t('gateway.status.all')}
              value={statusFilter}
              options={statusOptions}
              onChange={(value) => setStatusFilter(value)}
            />
          </Space>
        )}
      />

      <FormModal<RuleFormValues>
        open={editorOpen}
        title={
          editRecord
            ? t('gateway.form.editTitle', { name: title, path: editRecord.pathPattern })
            : t('gateway.form.createTitle', { name: title })
        }
        form={editorForm}
        initialValues={editRecord ? toFormValues(editRecord) : { status: 1, sortNo: 0 }}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="pathPattern"
          label={t('gateway.form.path')}
          rules={[
            { required: true, message: t('gateway.form.pathRequired') },
            { pattern: /^\/.*/, message: t('gateway.form.pathInvalid') },
          ]}
        >
          <Input placeholder={t('gateway.form.pathPlaceholder')} maxLength={255} />
        </Form.Item>
        <Form.Item
          name="httpMethod"
          label={t('gateway.form.method')}
          tooltip={t('gateway.form.methodTooltip')}
        >
          <Select
            allowClear
            options={HTTP_METHOD_OPTIONS}
            placeholder={t('gateway.form.methodPlaceholder')}
          />
        </Form.Item>
        <Form.Item name="status" label={t('gateway.form.status')}>
          <Select options={statusOptions} />
        </Form.Item>
        <Form.Item
          name="sortNo"
          label={t('gateway.form.sortNo')}
          tooltip={t('gateway.form.sortNoTooltip')}
        >
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="remark" label={t('gateway.form.remark')}>
          <Input.TextArea
            rows={2}
            maxLength={255}
            showCount
            placeholder={t('gateway.form.remarkPlaceholder')}
          />
        </Form.Item>
      </FormModal>
    </>
  );
}

export default function GatewayAccessList() {
  const { has } = usePermission();
  const { t } = useI18n();
  const items = (Object.keys(KINDS) as Kind[])
    .filter((kind) => has(KINDS[kind].listPerm))
    .map((kind) => ({
      key: kind,
      label: t(KINDS[kind].titleKey),
      children: <AccessRulePanel kind={kind} />,
    }));

  if (items.length === 0) {
    return <Alert type="error" showIcon message={t('gateway.missingPermission')} />;
  }

  return <Tabs defaultActiveKey={items[0].key} items={items} destroyOnHidden />;
}
