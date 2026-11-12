// BOM 列表：分页筛选、新建 EBOM 版本、打开结构编辑器
import { useCallback, useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import {
  createBom,
  deleteBom,
  listBoms,
  listParts,
  type BomStatus,
  type BomQuery,
  type BomView,
  type PdmBom,
  type PdmPart,
} from '../../api/part';
import { hasPermission } from '../../utils/permission';
import BomEditor from './BomEditor';

/** BOM 状态文案与颜色 */
const STATUS_CONFIG: Record<BomStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'blue' },
  RELEASED: { label: '已发布', color: 'green' },
  OBSOLETE: { label: '已失效', color: 'default' },
};

/** 可作为 BOM 产品的零件类型 */
const PRODUCT_TYPES = new Set(['PRODUCT', 'ASSEMBLY']);

/** BOM 视图文案 */
const VIEW_OPTIONS: Array<{ value: BomView; label: string }> = [
  { value: 'EBOM', label: '设计 BOM' },
  { value: 'PBOM', label: '工艺 BOM' },
  { value: 'MBOM', label: '制造 BOM' },
];

interface BomFormValues {
  productId: string;
  bomNo?: string;
  remark?: string;
}

export default function BomLibrary() {
  const [filters, setFilters] = useState<BomQuery>({});
  const [rows, setRows] = useState<PdmBom[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const [formOpen, setFormOpen] = useState(false);
  const [form] = Form.useForm<BomFormValues>();
  const [products, setProducts] = useState<PdmPart[]>([]);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingBomId, setEditingBomId] = useState<string>('');

  const canAdd = hasPermission('sys:pdm:bom:add');
  const canDelete = hasPermission('sys:pdm:bom:delete');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listBoms({ current, size, ...filters });
      setRows(page.records);
      setTotal(Number(page.total));
    } finally {
      setLoading(false);
    }
  }, [current, size, filters]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const openCreate = async () => {
    form.resetFields();
    setFormOpen(true);
    try {
      const page = await listParts({ current: 1, size: 200, status: 'ENABLED' });
      setProducts(page.records.filter((part) => PRODUCT_TYPES.has(part.partType)));
    } catch {
      setProducts([]);
    }
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    await createBom({ ...values, view: 'EBOM' });
    message.success('BOM 已创建');
    setFormOpen(false);
    setRefreshKey((key) => key + 1);
  };

  const columns: TableProps<PdmBom>['columns'] = [
    { title: 'BOM 编号', dataIndex: 'bomNo', key: 'bomNo', width: 160 },
    { title: '产品编码', dataIndex: 'productCode', key: 'productCode', width: 140 },
    { title: '产品名称', dataIndex: 'productName', key: 'productName' },
    { title: '视图', dataIndex: 'view', key: 'view', width: 100, render: (value: BomView) => VIEW_OPTIONS.find((item) => item.value === value)?.label ?? value },
    { title: '版本', dataIndex: 'versionNo', key: 'versionNo', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: BomStatus) => {
        const config = STATUS_CONFIG[value] ?? { label: value, color: 'default' };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space size={0}>
          <Button
            type="link"
            onClick={() => {
              setEditingBomId(record.id);
              setEditorOpen(true);
            }}
          >
            打开结构
          </Button>
          {canDelete && record.status === 'DRAFT' ? (
            <Popconfirm
              title="确认删除该草稿 BOM？"
              onConfirm={async () => {
                await deleteBom(record.id);
                message.success('删除成功');
                setRefreshKey((key) => key + 1);
              }}
            >
              <Button type="link" danger>
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
      <Space wrap>
        <Input
          allowClear
          placeholder="BOM 编号"
          style={{ width: 180 }}
          onChange={(event) => setFilters((prev) => ({ ...prev, bomNo: event.target.value }))}
        />
        <Input
          allowClear
          placeholder="产品编码"
          style={{ width: 160 }}
          onChange={(event) => setFilters((prev) => ({ ...prev, productCode: event.target.value }))}
        />
        <Select
          allowClear
          placeholder="视图"
          style={{ width: 130 }}
          options={VIEW_OPTIONS}
          onChange={(value) => setFilters((prev) => ({ ...prev, view: value as BomView }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          options={Object.entries(STATUS_CONFIG).map(([value, config]) => ({
            value,
            label: config.label,
          }))}
          onChange={(value) => setFilters((prev) => ({ ...prev, status: value as BomStatus }))}
        />
        <Button type="primary" onClick={() => setCurrent(1)}>
          查询
        </Button>
        {canAdd ? (
          <Button type="primary" onClick={() => void openCreate()}>
            新建 BOM
          </Button>
        ) : null}
      </Space>

      <Table<PdmBom>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />

      <Modal
        open={formOpen}
        title="新建 BOM"
        onOk={handleCreate}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="productId"
            label="产品"
            rules={[{ required: true, message: '请选择产品' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="请选择成品或部件"
              options={products.map((part) => ({
                value: part.id,
                label: `${part.code} - ${part.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="bomNo" label="BOM 编号">
            <Input placeholder="留空按产品编码自动生成" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      <BomEditor
        open={editorOpen}
        bomId={editingBomId}
        onClose={() => setEditorOpen(false)}
        onChanged={() => setRefreshKey((key) => key + 1)}
      />
    </Space>
  );
}
