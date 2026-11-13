// ERP 安全库存页：配置维护 / 缺料预警 / 呆滞分析三个页签
import { useCallback, useEffect, useState } from 'react';
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
import type { TableProps } from 'antd';
import {
  createSafetyStock,
  deleteSafetyStock,
  pageIdleStocks,
  pageSafetyStocks,
  pageShortages,
  updateSafetyStock,
  type IdleStockView,
  type SafetyStockView,
  type ShortageView,
} from '../api/scm';
import { listErpItems, type ErpItem } from '../api/erp';
import { pageWarehouses, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { money, quantity } from '../utils/scm';

/** 安全库存表单值 */
interface SafetyStockFormValues {
  warehouseId: number;
  itemCode: string;
  safetyQty: number;
  maxQty?: number;
  remark?: string;
}

/** 仓库选项 */
interface WarehouseOption {
  value: number;
  label: string;
}

/** 仓库名称映射 */
function warehouseName(options: WarehouseOption[], id: number | undefined): string {
  return options.find((option) => option.value === id)?.label ?? String(id ?? '-');
}

/** 安全库存配置页签 */
function SafetyStockConfigTab() {
  const [data, setData] = useState<SafetyStockView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SafetyStockView | null>(null);
  const [form] = Form.useForm<SafetyStockFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSafetyStocks({ keyword, warehouseId, size: 50 });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, warehouseId]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    void pageWarehouses({ size: 100 }).then((page) => {
      setWarehouses(
        page.records.map((warehouse: ErpWarehouse) => ({
          value: warehouse.id,
          label: `${warehouse.code} ${warehouse.name}`,
        })),
      );
    });
    void listErpItems().then(setItems);
  }, []);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        warehouseId: editing.warehouseId,
        itemCode: editing.itemCode,
        safetyQty: editing.safetyQty,
        maxQty: editing.maxQty,
        remark: editing.remark,
      });
    } else {
      form.resetFields();
    }
  }, [open, editing, form]);

  const openCreate = () => {
    setEditing(null);
    setOpen(true);
  };

  const openEdit = (record: SafetyStockView) => {
    setEditing(record);
    setOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    if (editing) {
      await updateSafetyStock(editing.id, {
        safetyQty: Number(values.safetyQty),
        maxQty: values.maxQty === undefined ? undefined : Number(values.maxQty),
        remark: values.remark,
      });
      message.success('安全库存已更新');
    } else {
      await createSafetyStock({
        warehouseId: values.warehouseId,
        itemCode: values.itemCode,
        safetyQty: Number(values.safetyQty),
        maxQty: values.maxQty === undefined ? undefined : Number(values.maxQty),
        remark: values.remark,
      });
      message.success('安全库存已创建');
    }
    setOpen(false);
    setVersion((value) => value + 1);
  };

  const handleDelete = async (record: SafetyStockView) => {
    await deleteSafetyStock(record.id);
    message.success('安全库存已删除');
    setVersion((value) => value + 1);
  };

  const columns: TableProps<SafetyStockView>['columns'] = [
    {
      title: '仓库',
      key: 'warehouse',
      render: (_, record) => warehouseName(warehouses, record.warehouseId),
    },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    {
      title: '安全库存量',
      dataIndex: 'safetyQty',
      key: 'safetyQty',
      width: 110,
      render: quantity,
    },
    {
      title: '最高库存量',
      dataIndex: 'maxQty',
      key: 'maxQty',
      width: 110,
      render: (value: number | undefined) => (value === undefined ? '不设上限' : quantity(value)),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          {hasPermission('sys:erp:safety-stock:update') ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:erp:safety-stock:delete') ? (
            <Popconfirm title="确认删除该安全库存配置？" onConfirm={() => handleDelete(record)}>
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
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="物料编码 / 物料名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Select
          allowClear
          placeholder="全部仓库"
          style={{ width: 200 }}
          options={warehouses}
          value={warehouseId}
          onChange={(value) => setWarehouseId(value)}
        />
        {hasPermission('sys:erp:safety-stock:create') ? (
          <Button type="primary" onClick={openCreate}>
            新增安全库存
          </Button>
        ) : null}
      </Space>
      <Table<SafetyStockView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={open}
        title={editing ? '编辑安全库存' : '新增安全库存'}
        width={640}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="warehouseId"
              label="仓库"
              rules={[{ required: true, message: '请选择仓库' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 220 }}
                options={warehouses}
                disabled={Boolean(editing)}
              />
            </Form.Item>
            <Form.Item
              name="itemCode"
              label="物料"
              rules={[{ required: true, message: '请选择物料' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 220 }}
                disabled={Boolean(editing)}
                options={items.map((item) => ({
                  value: item.code,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="safetyQty"
              label="安全库存量"
              rules={[{ required: true, message: '请输入安全库存量' }]}
            >
              <InputNumber min={0} precision={4} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="maxQty" label="最高库存量（空表示不设上限）">
              <InputNumber min={0} precision={4} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 缺料预警页签 */
function ShortageTab() {
  const [data, setData] = useState<ShortageView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageShortages({ keyword, warehouseId, size: 100 });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, warehouseId]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    void pageWarehouses({ size: 100 }).then((page) => {
      setWarehouses(
        page.records.map((warehouse: ErpWarehouse) => ({
          value: warehouse.id,
          label: `${warehouse.code} ${warehouse.name}`,
        })),
      );
    });
  }, []);

  const columns: TableProps<ShortageView>['columns'] = [
    {
      title: '仓库',
      key: 'warehouse',
      render: (_, record) => warehouseName(warehouses, record.warehouseId),
    },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '在库数量', dataIndex: 'onHandQty', key: 'onHandQty', width: 110, render: quantity },
    { title: '安全库存量', dataIndex: 'safetyQty', key: 'safetyQty', width: 110, render: quantity },
    {
      title: '缺口数量',
      dataIndex: 'shortageQty',
      key: 'shortageQty',
      width: 110,
      render: (value: number) => <Tag color="red">{quantity(value)}</Tag>,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="物料编码 / 物料名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Select
          allowClear
          placeholder="全部仓库"
          style={{ width: 200 }}
          options={warehouses}
          value={warehouseId}
          onChange={(value) => setWarehouseId(value)}
        />
        <Button onClick={() => setVersion((value) => value + 1)}>刷新</Button>
        <Tag color="orange">仅展示缺口大于 0 的物料</Tag>
      </Space>
      <Table<ShortageView>
        rowKey={(record) => `${record.warehouseId}-${record.itemId}`}
        loading={loading}
        columns={columns}
        dataSource={data}
      />
    </Space>
  );
}

/** 呆滞分析页签 */
function IdleTab() {
  const [data, setData] = useState<IdleStockView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const [idleDays, setIdleDays] = useState(90);
  const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageIdleStocks({ keyword, warehouseId, idleDays, size: 100 });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, warehouseId, idleDays]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    void pageWarehouses({ size: 100 }).then((page) => {
      setWarehouses(
        page.records.map((warehouse: ErpWarehouse) => ({
          value: warehouse.id,
          label: `${warehouse.code} ${warehouse.name}`,
        })),
      );
    });
  }, []);

  const columns: TableProps<IdleStockView>['columns'] = [
    {
      title: '仓库',
      key: 'warehouse',
      render: (_, record) => warehouseName(warehouses, record.warehouseId),
    },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '结存数量', dataIndex: 'quantity', key: 'quantity', width: 110, render: quantity },
    { title: '结存金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
    {
      title: '最近出库',
      dataIndex: 'lastOutboundAt',
      key: 'lastOutboundAt',
      width: 170,
      render: (value: string | undefined) => value ?? '从未出库',
    },
    {
      title: '库龄（天）',
      dataIndex: 'idleDays',
      key: 'idleDays',
      width: 110,
      render: (value: number | undefined) => (value === undefined ? '—' : value),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="物料编码 / 物料名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Select
          allowClear
          placeholder="全部仓库"
          style={{ width: 200 }}
          options={warehouses}
          value={warehouseId}
          onChange={(value) => setWarehouseId(value)}
        />
        <InputNumber
          min={1}
          style={{ width: 140 }}
          value={idleDays}
          onChange={(value) => setIdleDays(Number(value ?? 90))}
          addonBefore="库龄≥"
          addonAfter="天"
        />
        <Button onClick={() => setVersion((value) => value + 1)}>刷新</Button>
      </Space>
      <Table<IdleStockView>
        rowKey={(record) => `${record.warehouseId}-${record.itemId}`}
        loading={loading}
        columns={columns}
        dataSource={data}
      />
    </Space>
  );
}

/** 安全库存页 */
export default function SafetyStockPage() {
  return (
    <Tabs
      defaultActiveKey="config"
      items={[
        { key: 'config', label: '安全库存', children: <SafetyStockConfigTab /> },
        { key: 'shortage', label: '缺料预警', children: <ShortageTab /> },
        { key: 'idle', label: '呆滞分析', children: <IdleTab /> },
      ]}
    />
  );
}
