// ERP 销售报价页：列表、新建（多分录）、客户接受、转销售订单与取消
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
  acceptSalesQuotation,
  cancelSalesQuotation,
  convertSalesQuotation,
  createSalesQuotation,
  getSalesQuotation,
  pageSalesQuotations,
  type QuotationLineView,
  type SalesQuotationView,
} from '../api/scm';
import { listErpItems, type ErpItem } from '../api/erp';
import {
  pageCustomers,
  pageWarehouses,
  type ErpCustomer,
  type ErpWarehouse,
} from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 销售报价状态文案（补充接受与转单状态） */
const QUOTATION_STATUS_LABEL: Record<string, string> = {
  ...SCM_STATUS_LABEL,
  ACCEPTED: '已接受',
  CONVERTED: '已转单',
};

/** 销售报价分录表单值 */
interface QuotationLineFormValues {
  itemCode: string;
  quantity?: number;
  unitPrice?: number;
  uom?: string;
}

/** 销售报价表单值 */
interface QuotationFormValues {
  customerId: number;
  docDate: Dayjs;
  validUntil?: Dayjs;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: QuotationLineFormValues[];
}

/** 销售报价页 */
export default function SalesQuotationPage() {
  const [data, setData] = useState<SalesQuotationView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<SalesQuotationView | null>(null);
  const [customers, setCustomers] = useState<ErpCustomer[]>([]);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<QuotationFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSalesQuotations({ size: 50, keyword, status });
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
      validUntil: dayjs().add(30, 'day'),
      currency: 'CNY',
      lines: [{ itemCode: '', quantity: 1, unitPrice: undefined }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createSalesQuotation({
      customerId: values.customerId,
      docDate: values.docDate.format('YYYY-MM-DD'),
      validUntil: values.validUntil?.format('YYYY-MM-DD'),
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
    message.success('销售报价单已创建（草稿）');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleAccept = async (id: number) => {
    await acceptSalesQuotation(id);
    message.success('销售报价单已被客户接受');
    setVersion((value) => value + 1);
  };

  const handleConvert = async (id: number) => {
    await convertSalesQuotation(id);
    message.success('销售报价已转为销售订单（草稿）');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelSalesQuotation(id);
    message.success('销售报价单已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getSalesQuotation(id));
  };

  /** 可取消状态：未转单、未取消 */
  const cancellable = (record: SalesQuotationView): boolean =>
    !['CONVERTED', 'CANCELLED'].includes(record.status);

  const columns: TableProps<SalesQuotationView>['columns'] = [
    { title: '报价单号', dataIndex: 'docNo', key: 'docNo', width: 170 },
    { title: '客户', dataIndex: 'customerName', key: 'customerName' },
    { title: '报价日期', dataIndex: 'docDate', key: 'docDate', width: 110 },
    { title: '有效期至', dataIndex: 'validUntil', key: 'validUntil', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value) => (
        <Tag color={scmStatusColor(String(value))}>
          {QUOTATION_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '报价金额', dataIndex: 'totalAmount', key: 'totalAmount', width: 120, render: money },
    { title: '已转订单号', dataIndex: 'convertedOrderNo', key: 'convertedOrderNo', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:sales-quotation:accept') ? (
            <Popconfirm title="确认客户已接受该报价？" onConfirm={() => handleAccept(record.id)}>
              <Button type="link" size="small">
                接受
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'ACCEPTED' && hasPermission('sys:erp:sales-quotation:convert') ? (
            <Popconfirm
              title="转单后将生成销售订单草稿，确认继续？"
              onConfirm={() => handleConvert(record.id)}
            >
              <Button type="link" size="small">
                转单
              </Button>
            </Popconfirm>
          ) : null}
          {cancellable(record) && hasPermission('sys:erp:sales-quotation:cancel') ? (
            <Popconfirm title="确认取消该销售报价单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<QuotationLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: quantity },
    { title: '报价单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 110, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="报价单号 / 客户"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button
          onClick={() => setStatus('DRAFT')}
          type={status === 'DRAFT' ? 'primary' : 'default'}
        >
          草稿
        </Button>
        <Button
          onClick={() => setStatus('ACCEPTED')}
          type={status === 'ACCEPTED' ? 'primary' : 'default'}
        >
          已接受
        </Button>
        <Button
          onClick={() => setStatus('CONVERTED')}
          type={status === 'CONVERTED' ? 'primary' : 'default'}
        >
          已转单
        </Button>
        {hasPermission('sys:erp:sales-quotation:create') ? (
          <Button type="primary" onClick={openCreate}>
            新增销售报价
          </Button>
        ) : null}
      </Space>
      <Table<SalesQuotationView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
      />
      <Modal
        open={createOpen}
        title="新增销售报价单"
        width={960}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="customerId"
              label="客户"
              rules={[{ required: true, message: '请选择客户' }]}
            >
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
            <Form.Item
              name="warehouseId"
              label="发货仓库"
              rules={[{ required: true, message: '请选择仓库' }]}
            >
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
            <Form.Item name="docDate" label="报价日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="validUntil" label="有效期至">
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
                      <InputNumber
                        placeholder="数量"
                        min={0.0001}
                        precision={4}
                        style={{ width: 120 }}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'unitPrice']}
                      rules={[{ required: true, message: '报价单价' }]}
                    >
                      <InputNumber
                        placeholder="报价单价"
                        min={0.01}
                        precision={2}
                        style={{ width: 120 }}
                      />
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
        title={detail ? `销售报价单 ${detail.docNo}` : '销售报价单详情'}
        footer={null}
        width={960}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>客户：{detail.customerName ?? detail.customerId}</span>
              <span>报价日期：{detail.docDate}</span>
              <span>有效期至：{detail.validUntil ?? '-'}</span>
              <span>状态：{QUOTATION_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>报价金额：{money(detail.totalAmount)}</span>
              <span>已转订单号：{detail.convertedOrderNo ?? '-'}</span>
            </Space>
            <Table<QuotationLineView>
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
