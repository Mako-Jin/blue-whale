// ERP 销售退货页：列表、新建（引用已发货订单行）、过账与取消
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
  cancelSalesReturn,
  createSalesReturn,
  getSalesOrder,
  getSalesReturn,
  pageSalesOrders,
  pageSalesReturns,
  postSalesReturn,
  type OrderLineView,
  type SalesOrderView,
  type SalesReturnView,
  type ReturnLineView,
} from '../api/scm';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 新建表单值 */
interface ReturnFormValues {
  salesOrderId: number;
  docDate: Dayjs;
  remark?: string;
}

/** 销售退货页 */
export default function SalesReturnPage() {
  const [data, setData] = useState<SalesReturnView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [orders, setOrders] = useState<SalesOrderView[]>([]);
  const [orderLines, setOrderLines] = useState<OrderLineView[]>([]);
  const [returnQuantities, setReturnQuantities] = useState<Record<number, number>>({});
  const [detail, setDetail] = useState<SalesReturnView | null>(null);
  const [form] = Form.useForm<ReturnFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSalesReturns({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const page = await pageSalesOrders({ size: 50 });
    setOrders(
      page.records.filter(
        (order) => order.status === 'SHIPPED' || order.status === 'PARTIALLY_SHIPPED',
      ),
    );
    setOrderLines([]);
    setReturnQuantities({});
    form.resetFields();
    form.setFieldsValue({ docDate: dayjs() });
    setCreateOpen(true);
  };

  const handleOrderChange = async (orderId: number) => {
    const order = await getSalesOrder(orderId);
    setOrderLines(order.lines ?? []);
    setReturnQuantities({});
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const lines = Object.entries(returnQuantities)
      .map(([orderLineNo, value]) => ({ orderLineNo: Number(orderLineNo), quantity: Number(value) }))
      .filter((line) => line.quantity > 0);
    if (lines.length === 0) {
      message.warning('请至少填写一行退货数量');
      return;
    }
    await createSalesReturn({
      salesOrderId: values.salesOrderId,
      docDate: values.docDate.format('YYYY-MM-DD'),
      remark: values.remark,
      lines,
    });
    message.success('销售退货单已创建（草稿）');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handlePost = async (id: number) => {
    await postSalesReturn(id);
    message.success('销售退货已过账：库存入库、应收冲减并生成红字凭证');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelSalesReturn(id);
    message.success('销售退货单已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getSalesReturn(id));
  };

  const columns: TableProps<SalesReturnView>['columns'] = [
    { title: '退货单号', dataIndex: 'docNo', key: 'docNo', width: 180 },
    { title: '退货日期', dataIndex: 'docDate', key: 'docDate', width: 110 },
    { title: '客户', dataIndex: 'customerName', key: 'customerName' },
    { title: '退货金额', dataIndex: 'totalAmount', key: 'totalAmount', width: 120, render: money },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={scmStatusColor(String(value))}>{SCM_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:sales-return:post') ? (
            <Popconfirm title="过账后将入库并冲减应收，确认继续？" onConfirm={() => handlePost(record.id)}>
              <Button type="link" size="small">
                过账
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'DRAFT' && hasPermission('sys:erp:sales-return:cancel') ? (
            <Popconfirm title="确认取消该退货单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<ReturnLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '订单行', dataIndex: 'orderLineNo', key: 'orderLineNo', width: 80 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: quantity },
    { title: '单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 100, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 110, render: money },
    { title: '单位成本', dataIndex: 'unitCost', key: 'unitCost', width: 110, render: money },
    { title: '成本金额', dataIndex: 'costAmount', key: 'costAmount', width: 110, render: money },
  ];

  const orderLineColumns: TableProps<OrderLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '已发', dataIndex: 'shippedQuantity', key: 'shippedQuantity', width: 100, render: quantity },
    { title: '单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 100, render: money },
    {
      title: '退货数量',
      key: 'returnQuantity',
      width: 140,
      render: (_, record) => (
        <InputNumber
          min={0}
          max={record.shippedQuantity ?? 0}
          precision={4}
          value={returnQuantities[record.lineNo]}
          onChange={(value) =>
            setReturnQuantities((current) => ({ ...current, [record.lineNo]: Number(value ?? 0) }))
          }
          style={{ width: 120 }}
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="退货单号 / 客户"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button onClick={() => setStatus('DRAFT')} type={status === 'DRAFT' ? 'primary' : 'default'}>
          草稿
        </Button>
        <Button onClick={() => setStatus('POSTED')} type={status === 'POSTED' ? 'primary' : 'default'}>
          已过账
        </Button>
        {hasPermission('sys:erp:sales-return:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增销售退货
          </Button>
        ) : null}
      </Space>
      <Table<SalesReturnView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增销售退货单"
        width={960}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="salesOrderId"
              label="源销售订单"
              rules={[{ required: true, message: '请选择销售订单' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 320 }}
                onChange={handleOrderChange}
                options={orders.map((order) => ({
                  value: order.id,
                  label: `${order.docNo} / ${order.customerName ?? ''}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="docDate" label="退货日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          <Table<OrderLineView>
            rowKey="lineNo"
            size="small"
            columns={orderLineColumns}
            dataSource={orderLines}
            pagination={false}
          />
          <Form.Item name="remark" label="备注" style={{ marginTop: 16 }}>
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={detail !== null}
        title={detail ? `销售退货单 ${detail.docNo}` : '销售退货单详情'}
        footer={null}
        width={960}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>客户：{detail.customerName}</span>
              <span>日期：{detail.docDate}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>金额：{money(detail.totalAmount)}</span>
            </Space>
            <Table<ReturnLineView>
              rowKey="lineNo"
              size="small"
              columns={lineColumns}
              dataSource={detail.lines ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Modal>
    </Space>
  );
}
