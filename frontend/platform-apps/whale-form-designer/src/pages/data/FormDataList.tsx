// 表单数据列表页（F8）：查询/分页 + 详情 Drawer（标签: 值有序展示）+ 删除
// 契约层 formDataApi / formApi 见 ../../api/form，本页只读不改
// 拆分：动态条件状态见 FormDataList/useDynamicFilters，筛选面板见 FormDataList/FilterPanel，
//   详情抽屉见 FormDataList/DetailDrawer，条件工具函数见 FormDataList/filterHelpers。

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  Popconfirm,
  Space,
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
  type ExportFormat,
  type FieldConfig,
  type FormDataItem,
  type Id,
} from '../../api/form';
import DetailDrawer from './FormDataList/DetailDrawer';
import FilterPanel from './FormDataList/FilterPanel';
import { serializeFilters } from './FormDataList/filterHelpers';
import { useDynamicFilters } from './FormDataList/useDynamicFilters';

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

/** 导出菜单项：CSV / Excel(xlsx) */
const EXPORT_MENU_ITEMS: MenuProps['items'] = [
  { key: 'csv', label: 'CSV' },
  { key: 'xlsx', label: 'Excel (xlsx)' },
];

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
  const dynamicFilter = useDynamicFilters(query.definitionCode);
  const { dynamicFields, dynamicFilters, clearFilters, resetDraft } = dynamicFilter;

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

  // 查询：读取表单值并回到第一页；表单编码变更时清空动态条件（旧字段可能不适用）
  const handleSearch = () => {
    const values = form.getFieldsValue();
    const nextCode = values.definitionCode?.trim() || undefined;
    const codeChanged = nextCode !== query.definitionCode;
    if (codeChanged) {
      clearFilters();
      resetDraft();
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
    clearFilters();
    resetDraft();
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
        {query.definitionCode && dynamicFields.length > 0 ? <FilterPanel filters={dynamicFilter} /> : null}
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

      <DetailDrawer
        open={drawerOpen}
        loading={detailLoading}
        detail={detail}
        fieldConfigs={fieldConfigs}
        onClose={() => setDrawerOpen(false)}
      />
    </div>
  );
}
