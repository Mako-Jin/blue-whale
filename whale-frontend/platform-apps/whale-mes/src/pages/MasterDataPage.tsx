// MES 基础主数据页：车间 / 产线 / 工作中心 / 工位 / 班组 / 班次 / 工序 七个页签
// 统一走 /mes/master-data/{kind} 接口，前端按类型渲染字段与下级引用下拉。
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TableProps, TabsProps } from 'antd';
import {
  deleteMesMasterData,
  listMesMasterDataOptions,
  pageMesMasterData,
  saveMesMasterData,
  type MasterDataKind,
  type MesMasterData,
  type MesMasterDataInput,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 主数据字典：类型 -> 启用记录 */
type Lookup = Partial<Record<MasterDataKind, MesMasterData[]>>;

/** 表单控件类型 */
type FieldControl = 'text' | 'textarea' | 'number' | 'select' | 'parent' | 'time';

/** 表单字段配置 */
interface FieldConfig {
  /** 字段名 */
  name: keyof MesMasterDataInput;
  /** 中文标签 */
  label: string;
  /** 控件类型 */
  control: FieldControl;
  /** 是否必填 */
  required?: boolean;
  /** 文本最大长度 */
  maxLength?: number;
  /** 数字精度 */
  precision?: number;
  /** 数字最小值 */
  min?: number;
  /** 静态选项 */
  options?: Array<{ value: string | number; label: string }>;
  /** 上级主数据类型（控件类型为 parent 时生效） */
  parentKind?: MasterDataKind;
  /** 输入占位提示 */
  placeholder?: string;
  /** 校验规则描述 */
  extra?: string;
}

/** 列配置：渲染函数可直接读取上级字典 */
interface ColumnConfig {
  /** 列标题 */
  title: string;
  /** 列宽 */
  width?: number;
  /** 单元格渲染 */
  render: (record: MesMasterData, lookups: Lookup) => ReactNode;
}

/** 单个主数据页签配置 */
interface KindConfig {
  /** 主数据类型 */
  kind: MasterDataKind;
  /** 页签标题 */
  label: string;
  /** 表单字段 */
  fields: FieldConfig[];
  /** 列表列 */
  columns: ColumnConfig[];
  /** 新建默认值 */
  defaults?: Record<string, unknown>;
}

/** 状态选项 */
const STATUS_OPTIONS = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '禁用' },
];

/** 工作中心类型选项 */
const CENTER_TYPE_OPTIONS = [
  { value: 'MACHINE', label: '设备' },
  { value: 'LINE', label: '产线' },
  { value: 'CELL', label: '单元' },
];

/** 工序类型选项 */
const OPERATION_TYPE_OPTIONS = [
  { value: 'PROCESS', label: '加工' },
  { value: 'INSPECT', label: '检验' },
  { value: 'PACK', label: '包装' },
];

/** 跨日选项 */
const CROSS_DAY_OPTIONS = [
  { value: 0, label: '否' },
  { value: 1, label: '是' },
];

/** 影响可用率选项 */
const AFFECT_OPTIONS = [
  { value: 0, label: '否' },
  { value: 1, label: '是' },
];

/** 时间格式校验 */
const TIME_PATTERN = /^\d{2}:\d{2}(:\d{2})?$/;

/** 基础字段：编码 / 名称 */
const CODE_NAME_FIELDS: FieldConfig[] = [
  { name: 'code', label: '编码', control: 'text', required: true, maxLength: 64 },
  { name: 'name', label: '名称', control: 'text', required: true, maxLength: 128 },
];

/** 基础字段：状态 / 备注 */
const STATUS_REMARK_FIELDS: FieldConfig[] = [
  { name: 'status', label: '状态', control: 'select', required: true, options: STATUS_OPTIONS },
  { name: 'remark', label: '备注', control: 'textarea' },
];

/** 状态标签 */
function StatusTag({ status }: { status: string }) {
  return <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '禁用'}</Tag>;
}

/** 上级引用展示：编码 + 名称，未命中时回退为 ID */
function parentLabel(lookups: Lookup, kind: MasterDataKind, id?: string): string {
  if (!id) return '-';
  const found = lookups[kind]?.find((item) => item.id === id);
  return found ? `${found.code} ${found.name}`.trim() : id;
}

/** 七个主数据页签配置 */
const KIND_CONFIGS: KindConfig[] = [
  {
    kind: 'workshop',
    label: '车间',
    fields: [
      ...CODE_NAME_FIELDS,
      { name: 'address', label: '车间位置', control: 'text', maxLength: 255 },
      { name: 'managerName', label: '车间负责人', control: 'text', maxLength: 64 },
      ...STATUS_REMARK_FIELDS,
    ],
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      { title: '负责人', width: 110, render: (record) => record.managerName ?? '-' },
      { title: '位置', render: (record) => record.address ?? '-' },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'line',
    label: '产线',
    fields: [
      ...CODE_NAME_FIELDS,
      {
        name: 'workshopId',
        label: '所属车间',
        control: 'parent',
        parentKind: 'workshop',
        required: true,
      },
      ...STATUS_REMARK_FIELDS,
    ],
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      {
        title: '所属车间',
        width: 180,
        render: (record, lookups) => parentLabel(lookups, 'workshop', record.workshopId),
      },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'work-center',
    label: '工作中心',
    fields: [
      ...CODE_NAME_FIELDS,
      {
        name: 'workshopId',
        label: '所属车间',
        control: 'parent',
        parentKind: 'workshop',
        required: true,
      },
      { name: 'lineId', label: '所属产线', control: 'parent', parentKind: 'line', required: true },
      {
        name: 'centerType',
        label: '工作中心类型',
        control: 'select',
        required: true,
        options: CENTER_TYPE_OPTIONS,
      },
      {
        name: 'capacityPerShift',
        label: '每班产能',
        control: 'number',
        precision: 2,
        min: 0,
      },
      ...STATUS_REMARK_FIELDS,
    ],
    defaults: { centerType: 'MACHINE', capacityPerShift: 0 },
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      {
        title: '所属产线',
        width: 160,
        render: (record, lookups) => parentLabel(lookups, 'line', record.lineId),
      },
      { title: '类型', width: 90, render: (record) => record.centerType ?? '-' },
      { title: '每班产能', width: 110, render: (record) => String(record.capacityPerShift ?? 0) },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'station',
    label: '工位',
    fields: [
      ...CODE_NAME_FIELDS,
      { name: 'lineId', label: '所属产线', control: 'parent', parentKind: 'line', required: true },
      {
        name: 'workCenterId',
        label: '所属工作中心',
        control: 'parent',
        parentKind: 'work-center',
        required: true,
      },
      ...STATUS_REMARK_FIELDS,
    ],
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      {
        title: '所属产线',
        width: 160,
        render: (record, lookups) => parentLabel(lookups, 'line', record.lineId),
      },
      {
        title: '所属工作中心',
        width: 180,
        render: (record, lookups) => parentLabel(lookups, 'work-center', record.workCenterId),
      },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'team',
    label: '班组',
    fields: [
      ...CODE_NAME_FIELDS,
      {
        name: 'workshopId',
        label: '所属车间',
        control: 'parent',
        parentKind: 'workshop',
        required: true,
      },
      { name: 'leaderName', label: '班组长', control: 'text', maxLength: 64 },
      ...STATUS_REMARK_FIELDS,
    ],
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      {
        title: '所属车间',
        width: 180,
        render: (record, lookups) => parentLabel(lookups, 'workshop', record.workshopId),
      },
      { title: '班组长', width: 110, render: (record) => record.leaderName ?? '-' },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'shift',
    label: '班次',
    fields: [
      ...CODE_NAME_FIELDS,
      {
        name: 'startTime',
        label: '开始时间',
        control: 'time',
        required: true,
        placeholder: '如 08:00:00',
      },
      {
        name: 'endTime',
        label: '结束时间',
        control: 'time',
        required: true,
        placeholder: '如 20:00:00',
      },
      { name: 'crossDay', label: '是否跨日', control: 'select', options: CROSS_DAY_OPTIONS },
      ...STATUS_REMARK_FIELDS,
    ],
    defaults: { crossDay: 0 },
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      { title: '开始时间', width: 120, render: (record) => record.startTime ?? '-' },
      { title: '结束时间', width: 120, render: (record) => record.endTime ?? '-' },
      {
        title: '跨日',
        width: 80,
        render: (record) => (record.crossDay === 1 ? '是' : '否'),
      },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'operation',
    label: '工序',
    fields: [
      ...CODE_NAME_FIELDS,
      {
        name: 'operationType',
        label: '工序类型',
        control: 'select',
        required: true,
        options: OPERATION_TYPE_OPTIONS,
      },
      {
        name: 'standardMinutes',
        label: '标准工时（分钟）',
        control: 'number',
        precision: 2,
        min: 0,
      },
      ...STATUS_REMARK_FIELDS,
    ],
    defaults: { operationType: 'PROCESS', standardMinutes: 0 },
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      { title: '类型', width: 90, render: (record) => record.operationType ?? '-' },
      { title: '标准工时', width: 110, render: (record) => String(record.standardMinutes ?? 0) },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'defect-code',
    label: '缺陷代码',
    fields: [
      ...CODE_NAME_FIELDS,
      { name: 'category', label: '缺陷分类', control: 'text', maxLength: 64 },
      ...STATUS_REMARK_FIELDS,
    ],
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      { title: '分类', width: 140, render: (record) => record.category ?? '-' },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
  {
    kind: 'reason-code',
    label: '异常原因',
    fields: [
      ...CODE_NAME_FIELDS,
      { name: 'category', label: '原因分类', control: 'text', maxLength: 64 },
      {
        name: 'affectAvailability',
        label: '影响 OEE 可用率',
        control: 'select',
        options: AFFECT_OPTIONS,
      },
      ...STATUS_REMARK_FIELDS,
    ],
    defaults: { affectAvailability: 0 },
    columns: [
      { title: '编码', width: 120, render: (record) => record.code },
      { title: '名称', render: (record) => record.name },
      { title: '分类', width: 140, render: (record) => record.category ?? '-' },
      {
        title: '影响可用率',
        width: 120,
        render: (record) => (record.affectAvailability === 1 ? '是' : '否'),
      },
      { title: '状态', width: 90, render: (record) => <StatusTag status={record.status} /> },
    ],
  },
];

/** 渲染表单控件 */
function renderControl(field: FieldConfig, lookups: Lookup) {
  switch (field.control) {
    case 'textarea':
      return <Input.TextArea rows={2} maxLength={255} />;
    case 'number':
      return (
        <InputNumber
          min={field.min ?? 0}
          precision={field.precision}
          style={{ width: '100%' }}
          placeholder={field.placeholder}
        />
      );
    case 'time':
      return <Input maxLength={8} placeholder={field.placeholder} />;
    case 'parent': {
      const options = (lookups[field.parentKind as MasterDataKind] ?? []).map((item) => ({
        value: item.id,
        label: `${item.code} ${item.name}`.trim(),
      }));
      return <Select allowClear showSearch optionFilterProp="label" options={options} />;
    }
    case 'select':
      return <Select options={field.options} />;
    default:
      return <Input maxLength={field.maxLength} placeholder={field.placeholder} />;
  }
}

/** 单个主数据页签 */
interface MasterDataTabProps {
  /** 页签配置 */
  config: KindConfig;
  /** 上级字典 */
  lookups: Lookup;
  /** 外部刷新令牌（父级字典变更后递增） */
  refreshToken: number;
  /** 保存 / 删除成功后通知父级刷新字典 */
  onChanged: () => void;
}

/** 主数据表单值：各字段均为标量 */
type MasterDataFormValues = Record<string, string | number | undefined>;

/** 主数据页签：列表 + 表单弹窗 */
function MasterDataTab({ config, lookups, refreshToken, onChanged }: MasterDataTabProps) {
  const [data, setData] = useState<MesMasterData[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesMasterData | null>(null);
  const [form] = Form.useForm<MasterDataFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesMasterData(config.kind, { size: 200, code: keyword || undefined });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [config.kind, keyword]);

  useEffect(() => {
    void load();
  }, [load, refreshToken]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue(editing as unknown as MasterDataFormValues);
    } else {
      form.resetFields();
      form.setFieldsValue((config.defaults ?? {}) as MasterDataFormValues);
    }
  }, [open, editing, form, config.defaults]);

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await saveMesMasterData(config.kind, {
      ...(config.defaults ?? {}),
      ...values,
      id: editing?.id,
    } as unknown as MesMasterDataInput);
    message.success('保存成功');
    setOpen(false);
    setEditing(null);
    onChanged();
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteMesMasterData(config.kind, id);
      message.success('删除成功');
      onChanged();
    } catch {
      message.error('删除失败：该主数据可能已被下级引用');
    }
  };

  const columns: TableProps<MesMasterData>['columns'] = [
    ...config.columns.map((column) => ({
      title: column.title,
      key: column.title,
      width: column.width,
      render: (_: unknown, record: MesMasterData) => column.render(record, lookups),
    })),
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: unknown, record: MesMasterData) => (
        <Space size="small">
          {hasPermission('sys:mes:master:edit') ? (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditing(record);
                setOpen(true);
              }}
            >
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:mes:master:delete') ? (
            <Popconfirm
              title="确认删除该主数据？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        <Input.Search
          allowClear
          placeholder="按编码搜索"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:mes:master:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增{config.label}
          </Button>
        ) : null}
      </Space>
      <Table<MesMasterData> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={`${editing ? '编辑' : '新增'}${config.label}`}
        onOk={handleSave}
        onCancel={() => {
          setOpen(false);
          setEditing(null);
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {config.fields.map((field) => (
            <Form.Item
              key={field.name}
              name={field.name}
              label={field.label}
              extra={field.extra}
              rules={
                field.required
                  ? [{ required: true, message: `请填写${field.label}` }]
                  : field.control === 'time'
                    ? [{ pattern: TIME_PATTERN, message: '时间格式应为 HH:mm:ss' }]
                    : undefined
              }
            >
              {renderControl(field, lookups)}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </Space>
  );
}

/** 需要作为上级下拉字典加载的主数据类型 */
const LOOKUP_KINDS: MasterDataKind[] = ['workshop', 'line', 'work-center', 'team'];

/** 主数据页 */
export default function MasterDataPage() {
  const [lookups, setLookups] = useState<Lookup>({});
  const [refreshToken, setRefreshToken] = useState(0);

  const loadLookups = useCallback(async () => {
    const results = await Promise.all(
      LOOKUP_KINDS.map(async (kind) => [kind, await listMesMasterDataOptions(kind)] as const),
    );
    setLookups(Object.fromEntries(results) as Lookup);
  }, []);

  useEffect(() => {
    void loadLookups();
  }, [loadLookups, refreshToken]);

  const items: TabsProps['items'] = KIND_CONFIGS.map((config) => ({
    key: config.kind,
    label: config.label,
    children: (
      <MasterDataTab
        config={config}
        lookups={lookups}
        refreshToken={refreshToken}
        onChanged={() => setRefreshToken((value) => value + 1)}
      />
    ),
  }));

  return <Tabs items={items} />;
}
