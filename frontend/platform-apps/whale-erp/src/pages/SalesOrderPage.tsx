// ERP 销售订单页：列表、新建（多分录）、审核、发货与取消
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  approveSalesOrder,
  cancelSalesOrder,
  createSalesOrder,
  getSalesOrder,
  pageSalesOrders,
  shipSalesOrder,
  type OrderLineView,
  type SalesOrderView,
} from '../api/scm';
import { listErpItems, type ErpItem } from '../api/erp';
import { pageCustomers, pageWarehouses, type ErpCustomer, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 可编辑的订单分录表单值 */
interface OrderLineFormValues {
  itemCode: string;
  quantity?: number;
  unitPrice?: number;
  uom?: string;
}

/** 销售订单表单值 */
interface OrderFormValues {
  customerId: number;
  docDate: Dayjs;
  deliveryDate?: Dayjs;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: OrderLineFormValues[];
}

/** 发货表单值 */
interface ShipFormValues {
  shipDate: Dayjs;
  lines: { lineNo: number; quantity?: number }[];
}

/** 分录剩余待发数量 */
function remainingOf(line: OrderLineView): number {
  return Number(line.quantity ?? 0) - Number(line.shippedQuantity ?? 0);
}

/** 销售订单页 */
export default function SalesOrderPage() {
  const [data, setData] = useState<SalesOrderView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<SalesOrderView | null>(null);
  const [shipOpen, setShipOpen] = useState(false);
  const [shipOrder, setShipOrder] = useState<SalesOrderView | null>(null);
  const [pendingLines, setPendingLines] = useState<OrderLineView[]>([]);
  const [customers, setCustomers] = useState<ErpCustomer[]>([]);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<OrderFormValues>();
  const [shipForm] = Form.useForm<ShipFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSalesOrders({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const [customerPage, warehousePage, itemList] = await Promise.all([
      pageCustomers({ size: 100 }),
      pageWarehouses({ size: 100 }),
      listErpItems(),
    ]);
    setCustomers(customerPage.records);
    setWarehouses(warehousePage.records);
    setItems(itemList);
    form.resetFields();
    form.setFieldsValue({
      docDate: dayjs(),
      deliveryDate: dayjs().add(7, 'day'),
      currency: 'CNY',
      lines: [{ itemCode: '', quantity: 1, unitPrice: undefined }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createSalesOrder({
      customerId: values.customerId,
      docDate: values.docDate.format('YYYY-MM-DD'),
      deliveryDate: values.deliveryDate?.format('YYYY-MM-DD'),
      warehouseId: values.warehouseId,
      currency: values.currency,
      remark: values.remark,
      lines: values.lines.map((line) => ({
        itemCode: line.itemCode,
        quantity: Number(line.quantity ?? 0),
        unitPrice: Number(line.unitPrice ?? 0),
        uom: line.uom,
      })),
    });
    message.success('销售订单已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleApprove = async (id: number) => {
    await approveSalesOrder(id);
    message.success('销售订单已审核');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelSalesOrder(id);
    message.success('销售订单已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getSalesOrder(id));
  };

  const openShip = async (id: number) => {
    const order = await getSalesOrder(id);
    const pending = (order.lines ?? []).filter((line) => remainingOf(line) > 0);
    if (pending.length === 0) {
      message.warning('该订单已全部发货');
      return;
    }
    setPendingLines(pending);
    setShipOrder(order);
    shipForm.resetFields();
    shipForm.setFieldsValue({
      shipDate: dayjs(),
      lines: pending.map((line) => ({ lineNo: line.lineNo, quantity: remainingOf(line) })),
    });
    setShipOpen(true);
  };

  const handleShip = async () => {
    if (!shipOrder) return;
    const values = await shipForm.validateFields().catch(() => null);
    if (!values) return;
    await shipSalesOrder(shipOrder.id, {
      shipDate: values.shipDate.format('YYYY-MM-DD'),
      lines: (values.lines ?? []).map((line) => ({
        lineNo: Number(line.lineNo),
        quantity: Number(line.quantity ?? 0),
      })),
    });
    message.success('销售发货已提交，库存与应收已同步');
    setShipOpen(false);
    setShipOrder(null);
    setVersion((value) => value + 1);
  };

  /** 可取消状态：未发货、未关闭、未取消 */
  const cancellable = (order: SalesOrderView): boolean =>
    !['SHIPPED', 'CLOSED', 'CANCELLED'].includes(order.status);

  const columns: TableProps<SalesOrderView>['columns'] = [
    { title: '订单号', dataIndex: 'docNo', key: 'docNo', width: 170 },
    { title: '客户', dataIndex: 'customerName', key: 'customerName' },
    { title: '单据日期', dataIndex: 'docDate', key: 'docDate', width: 110 },
    { title: '计划发货', dataIndex: 'deliveryDate', key: 'deliveryDate', width: 110 },
    { title: '订单金额', dataIndex: 'totalAmount', key: 'totalAmount', width: 120, render: money },
    { title: '已发金额', dataIndex: 'shippedAmount', key: 'shippedAmount', width: 120, render: money },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value) => (
        <Tag color={scmStatusColor(String(value))}>{SCM_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:sales:approve') ? (
            <Popconfirm title="审核后订单不可修改，确认继续？" onConfirm={() => handleApprove(record.id)}>
              <Button type="link" size="small">
                审核
              </Button>
            </Popconfirm>
          ) : null}
          {['APPROVED', 'PARTIALLY_SHIPPED'].includes(record.status)
            && hasPermission('sys:erp:sales:ship') ? (
            <Button type="link" size="small" onClick={() => openShip(record.id)}>
              发货
            </Button>
          ) : null}
          {cancellable(record) && hasPermission('sys:erp:sales:cancel') ? (
            <Popconfirm title="确认取消该销售订单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<OrderLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: quantity },
    {
      title: '已发',
      dataIndex: 'shippedQuantity',
      key: 'shippedQuantity',
      width: 100,
      render: quantity,
    },
    { title: '单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 110, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="订单号 / 客户"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button onClick={() => setStatus('DRAFT')} type={status === 'DRAFT' ? 'primary' : 'default'}>
          草稿
        </Button>
        <Button onClick={() => setStatus('APPROVED')} type={status === 'APPROVED' ? 'primary' : 'default'}>
          已审核
        </Button>
        <Button onClick={() => setStatus('SHIPPED')} type={status === 'SHIPPED' ? 'primary' : 'default'}>
          已发货
        </Button>
        {hasPermission('sys:erp:sales:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增销售订单
          </Button>
        ) : null}
      </Space>
      <Table<SalesOrderView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增销售订单"
        width={960}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item name="customerId" label="客户" rules={[{ required: true, message: '请选择客户' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
                options={customers.map((item) => ({
                  value: item.id,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="warehouseId" label="发货仓库" rules={[{ required: true, message: '请选择仓库' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 200 }}
                options={warehouses.map((item) => ({
                  value: item.id,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="docDate" label="单据日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="deliveryDate" label="计划发货">
              <DatePicker />
            </Form.Item>
            <Form.Item name="currency" label="币种">
              <Input style={{ width: 100 }} maxLength={8} />
            </Form.Item>
          </Space>
          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'itemCode']}
                      rules={[{ required: true, message: '请选择物料' }]}
                    >
                      <Select
                        showSearch
                        optionFilterProp="label"
                        placeholder="物料"
                        style={{ width: 240 }}
                        options={items.map((item) => ({
                          value: item.code,
                          label: `${item.code} ${item.name}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'quantity']}
                      rules={[{ required: true, message: '数量' }]}
                    >
                      <InputNumber placeholder="数量" min={0.0001} precision={4} style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'unitPrice']}
                      rules={[{ required: true, message: '单价' }]}
                    >
                      <InputNumber placeholder="单价" min={0.01} precision={2} style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'uom']}>
                      <Input placeholder="单位" style={{ width: 90 }} maxLength={16} />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add({ quantity: 1 })} block>
                  增加分录
                </Button>
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={detail !== null}
        title={detail ? `销售订单 ${detail.docNo}` : '销售订单详情'}
        footer={null}
        width={960}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>客户：{detail.customerName ?? detail.customerId}</span>
              <span>单据日期：{detail.docDate}</span>
              <span>计划发货：{detail.deliveryDate ?? '-'}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>订单金额：{money(detail.totalAmount)}</span>
              <span>已发金额：{money(detail.shippedAmount)}</span>
            </Space>
            <Table<OrderLineView>
              rowKey="lineNo"
              size="small"
              columns={lineColumns}
              dataSource={detail.lines ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Modal>
      <Modal
        open={shipOpen}
        title={shipOrder ? `销售发货 ${shipOrder.docNo}` : '销售发货'}
        onOk={handleShip}
        onCancel={() => {
          setShipOpen(false);
          setShipOrder(null);
        }}
        destroyOnHidden
      >
        <Form form={shipForm} layout="vertical">
          <Form.Item name="shipDate" label="发货日期" rules={[{ required: true }]}>
            <DatePicker />
          </Form.Item>
          {pendingLines.map((line, index) => (
            <Form.Item
              key={line.lineNo}
              name={['lines', index, 'quantity']}
              label={`第 ${line.lineNo} 行 ${line.itemCode}（剩余 ${quantity(remainingOf(line))}）`}
              rules={[{ required: true, message: '请输入本次发货数量' }]}
            >
              <InputNumber min={0} max={remainingOf(line)} precision={4} style={{ width: '100%' }} />
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </Space>
  );
}
