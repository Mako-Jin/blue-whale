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
  title: string;
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
    title: '白名单',
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
    title: '黑名单',
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

const STATUS_OPTIONS = [
  { value: 1, label: '启用/生效' },
  { value: 0, label: '停用/解除' },
];

/** 来源标签：迁移种子与手工维护区分展示 */
const SOURCE_LABELS: Record<string, string> = {
  MIGRATION: '迁移',
  MANUAL: '手工',
};

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
      message.error(error instanceof Error ? error.message : `${meta.title}加载失败`);
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
      message.success(`${meta.title}条目已删除`);
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
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
      message.success(`${next === 1 ? '已启用' : '已停用'}，网关 60s 内生效`);
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '状态更新失败');
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
        message.success(`${meta.title}条目已更新`);
      } else {
        await meta.create(payload);
        message.success(`${meta.title}条目已创建`);
      }
      setEditorOpen(false);
      void load();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : editRecord ? '更新失败' : '创建失败',
      );
      throw error;
    }
  };

  const columns: TableProps<GatewayAccessRuleVO>['columns'] = [
    {
      title: '路径',
      dataIndex: 'pathPattern',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: '方法',
      dataIndex: 'httpMethod',
      width: 100,
      render: (value: string | undefined) => <Tag>{value || '*'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has(meta.editPerm)}
          checkedChildren="启用"
          unCheckedChildren="停用"
          onChange={(checked) => void handleToggle(row, checked ? 1 : 0)}
        />
      ),
    },
    { title: '排序', dataIndex: 'sortNo', width: 80 },
    {
      title: '来源',
      dataIndex: 'source',
      width: 100,
      render: (value: string | undefined) => (value ? (SOURCE_LABELS[value] ?? value) : '-'),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
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
            perm={meta.editPerm}
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            编辑
          </AuthButton>
          {has(meta.deletePerm) && (
            <Popconfirm
              title={`确认删除该${meta.title}条目？`}
              description="删除为物理删除；仅临时停用请使用状态开关。"
              onConfirm={() => void handleDelete(row)}
            >
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
      {kind === 'blacklist' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="黑名单优先于白名单：命中黑名单的请求一律 403，不再判断白名单与令牌。"
          description="误封 /auth/v1/login 等登录路径会导致全平台无法登录，新增前请确认路径范围。"
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
            <span>{meta.title}列表</span>
            <AuthButton
              perm={meta.addPerm}
              type="primary"
              size="small"
              onClick={() => {
                setEditRecord(null);
                setEditorOpen(true);
              }}
            >
              新增{meta.title}
            </AuthButton>
            <Select<number>
              allowClear
              size="small"
              style={{ width: 150 }}
              placeholder="全部状态"
              value={statusFilter}
              options={STATUS_OPTIONS}
              onChange={(value) => setStatusFilter(value)}
            />
          </Space>
        )}
      />

      <FormModal<RuleFormValues>
        open={editorOpen}
        title={
          editRecord ? `编辑${meta.title}：${editRecord.pathPattern}` : `新增${meta.title}`
        }
        form={editorForm}
        initialValues={editRecord ? toFormValues(editRecord) : { status: 1, sortNo: 0 }}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="pathPattern"
          label="路径"
          rules={[
            { required: true, message: '请输入路径' },
            { pattern: /^\/.*/, message: '路径必须以 / 开头' },
          ]}
        >
          <Input placeholder="如 /music/stream/**" maxLength={255} />
        </Form.Item>
        <Form.Item name="httpMethod" label="HTTP 方法" tooltip="不选表示不限方法（*）">
          <Select allowClear options={HTTP_METHOD_OPTIONS} placeholder="不限方法" />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="sortNo" label="排序号" tooltip="数值小者优先">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea
            rows={2}
            maxLength={255}
            showCount
            placeholder="放行原因 / 封禁原因或工单号"
          />
        </Form.Item>
      </FormModal>
    </>
  );
}

export default function GatewayAccessList() {
  const { has } = usePermission();
  const items = (Object.keys(KINDS) as Kind[])
    .filter((kind) => has(KINDS[kind].listPerm))
    .map((kind) => ({
      key: kind,
      label: KINDS[kind].title,
      children: <AccessRulePanel kind={kind} />,
    }));

  if (items.length === 0) {
    return <Alert type="error" showIcon message="缺少网关访问名单的查询权限（sys:gateway:*:list）" />;
  }

  return <Tabs defaultActiveKey={items[0].key} items={items} destroyOnHidden />;
}
