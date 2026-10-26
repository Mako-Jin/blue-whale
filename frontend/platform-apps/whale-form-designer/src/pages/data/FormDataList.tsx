// 表单数据列表页（F8）：查询/分页 + 详情 Drawer（标签: 值有序展示）+ 删除
// 契约层 formDataApi / formApi 见 ../../api/form，本页只读不改

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  type MenuProps,
  type TableColumnsType,
} from 'antd';
import { useSearchParams } from 'react-router-dom';
import {
  formApi,
  formDataApi,
  type DataFilter,
  type DataType,
  type ExportFormat,
  type FieldConfig,
  type FilterOp,
  type FormDataItem,
  type Id,
} from '../../api/form';

/** 页面查询状态：分页 + 过滤条件 */
interface QueryState {
  current: number;
  size: number;
  definitionCode?: string;
  bizKey?: string;
  submitter?: string;
  /** 动态字段条件（JSON 数组文本，仅在 definitionCode 存在时生效） */
  filters?: string;
}

/** 查询表单字段 */
interface QueryFormValues {
  definitionCode?: string;
  bizKey?: string;
  submitter?: string;
}

/** 详情行：一条「标签: 值」 */
interface DetailRow {
  label: string;
  value: string;
}

/** 详情视图：有序行 + 是否解析失败 */
interface DetailView {
  rows: DetailRow[];
  parseError: boolean;
}

/** 导出菜单项：CSV / Excel(xlsx) */
const EXPORT_MENU_ITEMS: MenuProps['items'] = [
  { key: 'csv', label: 'CSV' },
  { key: 'xlsx', label: 'Excel (xlsx)' },
];

/** 支持动态条件检索的列类型（后端 FormDataType.isFilterable 白名单） */
const FILTERABLE_TYPES = new Set<DataType>([
  'text',
  'textarea',
  'number',
  'date',
  'datetime',
  'select',
  'bool',
]);

/** 操作符中文名 */
const FILTER_OP_LABELS: Record<FilterOp, string> = {
  eq: '等于',
  ne: '不等于',
  gt: '大于',
  gte: '大于等于',
  lt: '小于',
  lte: '小于等于',
  like: '包含',
  in: '属于',
  between: '介于',
  isNull: '为空',
  notNull: '不为空',
};

/** 按列类型给出可用操作符（与后端类型-操作符校验一致） */
function filterOpsFor(dataType: DataType | undefined): FilterOp[] {
  switch (dataType) {
    case 'number':
    case 'date':
    case 'datetime':
      return ['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'isNull', 'notNull'];
    case 'bool':
      return ['eq', 'ne', 'isNull', 'notNull'];
    default:
      return ['eq', 'ne', 'like', 'isNull', 'notNull'];
  }
}

/** 操作符是否需要填写值 */
function needsValue(op: FilterOp): boolean {
  return op !== 'isNull' && op !== 'notNull';
}

/** 动态条件序列化为查询参数（空条件返回 undefined，避免请求携带无用字段） */
function serializeFilters(filters: DataFilter[]): string | undefined {
  return filters.length > 0 ? JSON.stringify(filters) : undefined;
}

/** 按列编码取字段中文名（缺失时回落编码） */
function fieldLabelOf(fields: FieldConfig[], code: string): string {
  return fields.find((field) => field.columnCode === code)?.fieldLabel || code;
}

/** 空值统一显示为短横线，其余原样字符串化（checkbox 的逗号串不拆分） */
function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

/**
 * 组装详情展示行：
 * - fields 非空：按 fieldConfigs 中 columnCode 的顺序输出「fieldLabel: 值」，跳过纯布局字段；
 * - fields 为 null（旧模型/列表态）：兜底 JSON.parse(dataJson) 输出键值对，键命中则用中文标签；
 * - dataJson 解析失败：置 parseError。
 */
function buildDetailView(detail: FormDataItem | null, fieldConfigs: FieldConfig[]): DetailView {
  if (!detail) {
    return { rows: [], parseError: false };
  }

  // columnCode -> fieldLabel 映射，供兜底解析时还原中文标签
  const labelMap = new Map<string, string>();
  fieldConfigs.forEach((config) => {
    if (config.columnCode) {
      labelMap.set(config.columnCode, config.fieldLabel || config.columnCode);
    }
  });

  const fields = detail.fields;
  if (fields) {
    const rows: DetailRow[] = [];
    fieldConfigs.forEach((config) => {
      if (!config.columnCode) {
        return;
      }
      rows.push({
        label: config.fieldLabel || config.columnCode,
        value: formatValue(fields[config.columnCode]),
      });
    });
    return { rows, parseError: false };
  }

  // fields 为空：兜底解析落库的 dataJson
  try {
    const parsed = JSON.parse(detail.dataJson || '{}') as Record<string, unknown>;
    const rows: DetailRow[] = Object.entries(parsed).map(([key, value]) => ({
      label: labelMap.get(key) ?? key,
      value: formatValue(value),
    }));
    return { rows, parseError: false };
  } catch {
    return { rows: [], parseError: true };
  }
}

export default function FormDataList() {
  const { message } = App.useApp();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<QueryFormValues>();

  // URL query 的 definitionCode 作为默认过滤值
  const initialCode = useMemo(() => searchParams.get('definitionCode') ?? '', [searchParams]);

  const [query, setQuery] = useState<QueryState>(() => ({
    current: 1,
    size: 10,
    definitionCode: initialCode || undefined,
  }));
  const [dataSource, setDataSource] = useState<FormDataItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  // 导出中：禁用导出按钮，避免重复触发
  const [exporting, setExporting] = useState(false);

  // 动态字段筛选：可选字段（按表单编码加载）、已添加条件、草稿（字段/操作符/值）
  const [dynamicFields, setDynamicFields] = useState<FieldConfig[]>([]);
  const [dynamicFilters, setDynamicFilters] = useState<DataFilter[]>([]);
  const [draftField, setDraftField] = useState<string | undefined>(undefined);
  const [draftOp, setDraftOp] = useState<FilterOp>('eq');
  const [draftValue, setDraftValue] = useState<string | number | undefined>(undefined);

  const selectedField = useMemo(
    () => dynamicFields.find((field) => field.columnCode === draftField),
    [dynamicFields, draftField],
  );
  const draftOps = useMemo(() => filterOpsFor(selectedField?.dataType), [selectedField]);

  // 详情 Drawer 状态
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<FormDataItem | null>(null);
  const [fieldConfigs, setFieldConfigs] = useState<FieldConfig[]>([]);

  // 拉取列表：受控分页 + 过滤条件（空串由 http 层自动忽略）
  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await formDataApi.page({
        current: query.current,
        size: query.size,
        definitionCode: query.definitionCode,
        bizKey: query.bizKey,
        submitter: query.submitter,
        filters: query.filters,
      });
      setDataSource(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  // 按表单编码加载可筛选字段：取该编码下最新版本定义，过滤掉不支持检索的列类型
  useEffect(() => {
    const code = query.definitionCode;
    if (!code) {
      setDynamicFields([]);
      return undefined;
    }
    let cancelled = false;
    void (async () => {
      try {
        const page = await formApi.page({ code, current: 1, size: 1 });
        const definition = page.records?.[0];
        if (!definition?.id) {
          if (!cancelled) {
            setDynamicFields([]);
          }
          return;
        }
        const detail = await formApi.detail(definition.id);
        if (!cancelled) {
          setDynamicFields(
            (detail.fields ?? []).filter(
              (field) => field.columnCode && field.dataType && FILTERABLE_TYPES.has(field.dataType),
            ),
          );
        }
      } catch {
        if (!cancelled) {
          setDynamicFields([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [query.definitionCode]);

  // 添加动态条件：数值/布尔做类型转换，为空类操作符不携带值
  const handleAddFilter = () => {
    if (!draftField) {
      message.warning('请选择过滤字段');
      return;
    }
    if (needsValue(draftOp) && (draftValue === undefined || draftValue === '')) {
      message.warning('请填写过滤值');
      return;
    }
    const withValue = needsValue(draftOp);
    const value = !withValue
      ? undefined
      : selectedField?.dataType === 'bool'
        ? draftValue === 'true'
        : draftValue;
    setDynamicFilters((prev) => [...prev, { field: draftField, op: draftOp, value }]);
    setDraftField(undefined);
    setDraftOp('eq');
    setDraftValue(undefined);
  };

  // 移除动态条件
  const handleRemoveFilter = (index: number) => {
    setDynamicFilters((prev) => prev.filter((_, current) => current !== index));
  };

  // 查询：读取表单值并回到第一页；表单编码变更时清空动态条件（旧字段可能不适用）
  const handleSearch = () => {
    const values = form.getFieldsValue();
    const nextCode = values.definitionCode?.trim() || undefined;
    const codeChanged = nextCode !== query.definitionCode;
    if (codeChanged) {
      setDynamicFilters([]);
      setDraftField(undefined);
      setDraftOp('eq');
      setDraftValue(undefined);
    }
    setQuery((prev) => ({
      ...prev,
      current: 1,
      definitionCode: nextCode,
      bizKey: values.bizKey?.trim() || undefined,
      submitter: values.submitter?.trim() || undefined,
      filters: codeChanged ? undefined : serializeFilters(dynamicFilters),
    }));
  };

  // 重置：清空过滤条件与动态条件并回到第一页
  const handleReset = () => {
    form.setFieldsValue({ definitionCode: '', bizKey: '', submitter: '' });
    setDynamicFilters([]);
    setDraftField(undefined);
    setDraftOp('eq');
    setDraftValue(undefined);
    setQuery({ current: 1, size: 10 });
  };

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    setQuery((prev) => ({
      ...prev,
      current: pagination.current ?? 1,
      size: pagination.pageSize ?? prev.size,
    }));
  };

  // 详情：并行拉取数据明细与表单定义（拿 fields[] 做标签映射）
  const handleDetail = async (record: FormDataItem) => {
    setDrawerOpen(true);
    setDetail(null);
    setFieldConfigs([]);
    setDetailLoading(true);
    try {
      const [data, definition] = await Promise.all([
        formDataApi.detail(record.id),
        formApi.detail(record.definitionId),
      ]);
      setDetail(data);
      setFieldConfigs(definition.fields ?? []);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setDetailLoading(false);
    }
  };

  // 删除：确认后调用接口，成功提示并重新加载（当前页删空且非首页则回退一页）
  const handleDelete = async (id: Id) => {
    try {
      await formDataApi.remove(id);
      message.success('删除成功');
      if (dataSource.length <= 1 && query.current > 1) {
        setQuery((prev) => ({ ...prev, current: prev.current - 1 }));
      } else {
        await loadList();
      }
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  // 导出：必填 definitionCode，按当前过滤条件导出原始字节流并触发浏览器下载
  const handleExport = async (format: ExportFormat) => {
    if (!query.definitionCode) {
      return;
    }
    setExporting(true);
    try {
      const { blob, filename } = await formDataApi.export({
        definitionCode: query.definitionCode,
        format,
        bizKey: query.bizKey,
        submitter: query.submitter,
        filters: query.filters,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      link.click();
      URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败');
    } finally {
      setExporting(false);
    }
  };

  const handleExportMenuClick: MenuProps['onClick'] = ({ key }) => {
    void handleExport(key as ExportFormat);
  };

  const detailView = useMemo(() => buildDetailView(detail, fieldConfigs), [detail, fieldConfigs]);

  const columns: TableColumnsType<FormDataItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '表单编码', dataIndex: 'definitionCode' },
    { title: '版本', dataIndex: 'definitionVersion', width: 80 },
    { title: '业务键', dataIndex: 'bizKey', render: (value: string) => value || '-' },
    { title: '提交人', dataIndex: 'submitter', render: (value: string) => value || '-' },
    { title: '提交时间', dataIndex: 'createTime', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>{value === 1 ? '正常' : '停用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => void handleDetail(record)}>
            详情
          </Button>
          <Popconfirm
            title="确认删除该条数据？"
            okText="删除"
            cancelText="取消"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Form
          form={form}
          layout="inline"
          initialValues={{ definitionCode: initialCode, bizKey: '', submitter: '' }}
        >
          <Form.Item name="definitionCode" label="表单编码">
            <Input allowClear placeholder="表单编码" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="bizKey" label="业务键">
            <Input allowClear placeholder="业务键" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="submitter" label="提交人">
            <Input allowClear placeholder="提交人" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleSearch}>
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
              {query.definitionCode ? (
                <Dropdown.Button
                  menu={{ items: EXPORT_MENU_ITEMS, onClick: handleExportMenuClick }}
                  loading={exporting}
                  onClick={() => void handleExport('csv')}
                >
                  导出
                </Dropdown.Button>
              ) : (
                <Tooltip title="请先选择表单编码">
                  <span style={{ display: 'inline-block' }}>
                    <Dropdown.Button menu={{ items: EXPORT_MENU_ITEMS }} disabled>
                      导出
                    </Dropdown.Button>
                  </span>
                </Tooltip>
              )}
            </Space>
          </Form.Item>
        </Form>
        {query.definitionCode && dynamicFields.length > 0 ? (
          <div style={{ marginTop: 12 }}>
            <Space wrap>
              <Select
                placeholder="过滤字段"
                style={{ width: 170 }}
                value={draftField}
                options={dynamicFields.map((field) => ({
                  value: field.columnCode as string,
                  label: field.fieldLabel || field.columnCode || '',
                }))}
                onChange={(value: string) => {
                  const nextField = dynamicFields.find((field) => field.columnCode === value);
                  setDraftField(value);
                  setDraftOp(filterOpsFor(nextField?.dataType)[0]);
                  setDraftValue(undefined);
                }}
              />
              <Select
                style={{ width: 120 }}
                value={draftOp}
                options={draftOps.map((op) => ({ value: op, label: FILTER_OP_LABELS[op] }))}
                onChange={(value: FilterOp) => setDraftOp(value)}
              />
              {needsValue(draftOp) ? (
                selectedField?.dataType === 'bool' ? (
                  <Select
                    placeholder="值"
                    style={{ width: 110 }}
                    value={draftValue as string | undefined}
                    options={[
                      { value: 'true', label: '是' },
                      { value: 'false', label: '否' },
                    ]}
                    onChange={(value: string) => setDraftValue(value)}
                  />
                ) : selectedField?.dataType === 'number' ? (
                  <InputNumber
                    placeholder="数值"
                    style={{ width: 140 }}
                    value={typeof draftValue === 'number' ? draftValue : undefined}
                    onChange={(value) => setDraftValue(value ?? undefined)}
                  />
                ) : (
                  <Input
                    allowClear
                    placeholder={selectedField?.dataType === 'date' ? 'yyyy-MM-dd' : '过滤值'}
                    style={{ width: 170 }}
                    value={typeof draftValue === 'string' ? draftValue : undefined}
                    onChange={(event) => setDraftValue(event.target.value)}
                  />
                )
              ) : null}
              <Button onClick={handleAddFilter}>添加条件</Button>
            </Space>
            {dynamicFilters.length > 0 ? (
              <div style={{ marginTop: 8 }}>
                {dynamicFilters.map((filter, index) => (
                  <Tag
                    key={`${filter.field}-${filter.op}-${index}`}
                    closable
                    onClose={() => handleRemoveFilter(index)}
                  >
                    {fieldLabelOf(dynamicFields, filter.field)} {FILTER_OP_LABELS[filter.op]}
                    {needsValue(filter.op) ? ` ${formatValue(filter.value)}` : ''}
                  </Tag>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </Card>

      <Table<FormDataItem>
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
        }}
        onChange={handleTableChange}
      />

      <Drawer
        title={detail ? `数据详情 #${detail.id}` : '数据详情'}
        width={560}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {detailView.parseError ? (
            <Alert
              type="warning"
              showIcon
              message="无法解析数据内容"
              description="dataJson 不是合法 JSON，无法展示明细。"
            />
          ) : detailView.rows.length === 0 ? (
            <div>暂无字段数据</div>
          ) : (
            <Descriptions
              column={1}
              bordered
              size="small"
              items={detailView.rows.map((row, index) => ({
                key: `${index}`,
                label: row.label,
                children: row.value,
              }))}
            />
          )}
        </Spin>
      </Drawer>
    </div>
  );
}
