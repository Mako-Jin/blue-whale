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
  Form,
  Input,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  type TableColumnsType,
} from 'antd';
import { useSearchParams } from 'react-router-dom';
import { formApi, formDataApi, type FieldConfig, type FormDataItem, type Id } from '../../api/form';

/** 页面查询状态：分页 + 过滤条件 */
interface QueryState {
  current: number;
  size: number;
  definitionCode?: string;
  bizKey?: string;
  submitter?: string;
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

  // 查询：读取表单值并回到第一页
  const handleSearch = () => {
    const values = form.getFieldsValue();
    setQuery((prev) => ({
      ...prev,
      current: 1,
      definitionCode: values.definitionCode?.trim() || undefined,
      bizKey: values.bizKey?.trim() || undefined,
      submitter: values.submitter?.trim() || undefined,
    }));
  };

  // 重置：清空过滤条件并回到第一页
  const handleReset = () => {
    form.setFieldsValue({ definitionCode: '', bizKey: '', submitter: '' });
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
            </Space>
          </Form.Item>
        </Form>
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
