// ERP 库存页：库存余额、出入库流水与库存调整（盘盈/盘亏）
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TabsProps, TableProps } from 'antd';
import {
  adjustStock,
  pageStockBalances,
  pageStockMovements,
  type StockAdjustInput,
  type StockBalanceView,
  type StockMovementView,
} from '../api/scm';
import { listErpItems, type ErpItem } from '../api/erp';
import { pageWarehouses, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { money, quantity } from '../utils/scm';

/** 页签入参：关键字 + 调整后刷新版本 */
interface TabProps {
  keyword: string;
  refreshKey: number;
}

/** 库存余额页签 */
function BalanceTab({ keyword, refreshKey }: TabProps) {
  const [data, setData] = useState<StockBalanceView[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageStockBalances({ size: 50, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const columns: TableProps<StockBalanceView>['columns'] = [
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '仓库', dataIndex: 'warehouseId', key: 'warehouseId', width: 100 },
    { title: '结存数量', dataIndex: 'quantity', key: 'quantity', width: 120, render: quantity },
    { title: '结存金额', dataIndex: 'amount', key: 'amount', width: 130, render: money },
  ];

  return (
    <Table<StockBalanceView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
  );
}

/** 库存流水页签 */
function MovementTab({ keyword, refreshKey }: TabProps) {
  const [data, setData] = useState<StockMovementView[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageStockMovements({ size: 50, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const columns: TableProps<StockMovementView>['columns'] = [
    { title: '单据号', dataIndex: 'docNo', key: 'docNo', width: 170 },
    { title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 130 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    {
      title: '方向',
      dataIndex: 'direction',
      key: 'direction',
      width: 90,
      render: (value) => (
        <Tag color={String(value) === 'IN' ? 'green' : 'red'}>
          {String(value) === 'IN' ? '入库' : '出库'}
        </Tag>
      ),
    },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 110, render: quantity },
    { title: '单位成本', dataIndex: 'unitCost', key: 'unitCost', width: 110, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
    { title: '发生时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 170 },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
  ];

  return (
    <Table<StockMovementView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
  );
}

/** 库存页 */
export default function StockPage() {
  const [keyword, setKeyword] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [form] = Form.useForm<StockAdjustInput>();

  const openAdjust = async () => {
    const [itemList, warehousePage] = await Promise.all([
      listErpItems(),
      pageWarehouses({ size: 100 }),
    ]);
    setItems(itemList);
    setWarehouses(warehousePage.records);
    form.resetFields();
    setAdjustOpen(true);
  };

  const handleAdjust = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await adjustStock({
      itemCode: values.itemCode,
      warehouseId: values.warehouseId,
      quantity: Number(values.quantity),
      remark: values.remark,
    });
    message.success('库存调整已提交');
    setAdjustOpen(false);
    setRefreshKey((value) => value + 1);
  };

  const items_: TabsProps['items'] = [
    {
      key: 'balance',
      label: '库存余额',
      children: <BalanceTab keyword={keyword} refreshKey={refreshKey} />,
    },
    {
      key: 'movement',
      label: '出入库流水',
      children: <MovementTab keyword={keyword} refreshKey={refreshKey} />,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="物料编码 / 名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:stock:adjust') ? (
          <Button type="primary" onClick={openAdjust}>
            库存调整
          </Button>
        ) : null}
      </Space>
      <Tabs items={items_} />
      <Modal
        open={adjustOpen}
        title="库存调整（正数入库 / 负数出库）"
        onOk={handleAdjust}
        onCancel={() => setAdjustOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="itemCode" label="物料" rules={[{ required: true, message: '请选择物料' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={items.map((item) => ({
                value: item.code,
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="warehouseId" label="仓库" rules={[{ required: true, message: '请选择仓库' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={warehouses.map((item) => ({
                value: item.id,
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="调整数量（正数盘盈、负数盘亏）"
            rules={[{ required: true, message: '请输入调整数量' }]}
          >
            <InputNumber precision={4} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
