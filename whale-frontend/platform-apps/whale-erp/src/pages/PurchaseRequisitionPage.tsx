// ERP 采购申请页：列表、新建（多分录）、审核、转采购订单与取消
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
  approvePurchaseRequisition,
  cancelPurchaseRequisition,
  convertPurchaseRequisition,
  createPurchaseRequisition,
  getPurchaseRequisition,
  pagePurchaseRequisitions,
  type PurchaseRequisitionView,
  type RequisitionLineView,
} from '../api/scm';
import { listErpItems, listErpSuppliers, type ErpItem, type ErpSupplier } from '../api/erp';
import { pageWarehouses, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 采购申请状态文案（补充转单状态） */
const REQUISITION_STATUS_LABEL: Record<string, string> = {
  ...SCM_STATUS_LABEL,
  CONVERTED: '已转单',
};

/** 采购申请分录表单值 */
interface RequisitionLineFormValues {
  itemCode: string;
  quantity?: number;
  unitPrice?: number;
  uom?: string;
}

/** 采购申请表单值 */
interface RequisitionFormValues {
  supplierId: number;
  docDate: Dayjs;
  requiredDate?: Dayjs;
  warehouseId: number;
  currency?: string;
  remark?: string;
  lines: RequisitionLineFormValues[];
}

/** 采购申请页 */
export default function PurchaseRequisitionPage() {
  const [data, setData] = useState<PurchaseRequisitionView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<PurchaseRequisitionView | null>(null);
  const [suppliers, setSuppliers] = useState<ErpSupplier[]>([]);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<RequisitionFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePurchaseRequisitions({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const [supplierList, warehousePage, itemList] = await Promise.all([
      listErpSuppliers(),
      pageWarehouses({ size: 100 }),
      listErpItems(),
    ]);
    setSuppliers(supplierList);
    setWarehouses(warehousePage.records);
    setItems(itemList);
    form.resetFields();
    form.setFieldsValue({
      docDate: dayjs(),
      requiredDate: dayjs().add(7, 'day'),
      currency: 'CNY',
      lines: [{ itemCode: '', quantity: 1, unitPrice: undefined }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createPurchaseRequisition({
      supplierId: values.supplierId,
      docDate: values.docDate.format('YYYY-MM-DD'),
      requiredDate: values.requiredDate?.format('YYYY-MM-DD'),
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
    message.success('采购申请单已创建（草稿）');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleApprove = async (id: number) => {
    await approvePurchaseRequisition(id);
    message.success('采购申请单已审核');
    setVersion((value) => value + 1);
  };

  const handleConvert = async (id: number) => {
    await convertPurchaseRequisition(id);
    message.success('采购申请已转为采购订单（草稿）');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelPurchaseRequisition(id);
    message.success('采购申请单已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getPurchaseRequisition(id));
  };

  /** 可取消状态：未转单、未取消 */
  const cancellable = (record: PurchaseRequisitionView): boolean =>
    !['CONVERTED', 'CANCELLED'].includes(record.status);

  const columns: TableProps<PurchaseRequisitionView>['columns'] = [
    { title: '申请单号', dataIndex: 'docNo', key: 'docNo', width: 170 },
    { title: '供应商', dataIndex: 'supplierName', key: 'supplierName' },
    { title: '申请日期', dataIndex: 'docDate', key: 'docDate', width: 110 },
    { title: '需求日期', dataIndex: 'requiredDate', key: 'requiredDate', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value) => (
        <Tag color={scmStatusColor(String(value))}>
          {REQUISITION_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '申请金额', dataIndex: 'totalAmount', key: 'totalAmount', width: 120, render: money },
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
          {record.status === 'DRAFT' && hasPermission('sys:erp:purchase-requisition:approve') ? (
            <Popconfirm
              title="审核后申请单不可修改，确认继续？"
              onConfirm={() => handleApprove(record.id)}
            >
              <Button type="link" size="small">
                审核
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'APPROVED' && hasPermission('sys:erp:purchase-requisition:convert') ? (
            <Popconfirm
              title="转单后将生成采购订单草稿，确认继续？"
              onConfirm={() => handleConvert(record.id)}
            >
              <Button type="link" size="small">
                转单
              </Button>
            </Popconfirm>
          ) : null}
          {cancellable(record) && hasPermission('sys:erp:purchase-requisition:cancel') ? (
            <Popconfirm title="确认取消该采购申请单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<RequisitionLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: quantity },
    { title: '预估单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 110, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="申请单号 / 供应商"
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
          onClick={() => setStatus('APPROVED')}
          type={status === 'APPROVED' ? 'primary' : 'default'}
        >
          已审核
        </Button>
        <Button
          onClick={() => setStatus('CONVERTED')}
          type={status === 'CONVERTED' ? 'primary' : 'default'}
        >
          已转单
        </Button>
        {hasPermission('sys:erp:purchase-requisition:create') ? (
          <Button type="primary" onClick={openCreate}>
            新增采购申请
          </Button>
        ) : null}
      </Space>
      <Table<PurchaseRequisitionView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
      />
      <Modal
        open={createOpen}
        title="新增采购申请单"
        width={960}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="supplierId"
              label="供应商"
              rules={[{ required: true, message: '请选择供应商' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
                options={suppliers.map((item) => ({
                  value: Number(item.id),
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="warehouseId"
              label="收货仓库"
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
            <Form.Item name="docDate" label="申请日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="requiredDate" label="需求日期">
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
                      rules={[{ required: true, message: '预估单价' }]}
                    >
                      <InputNumber
                        placeholder="预估单价"
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
        title={detail ? `采购申请单 ${detail.docNo}` : '采购申请单详情'}
        footer={null}
        width={960}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>供应商：{detail.supplierName ?? detail.supplierId}</span>
              <span>申请日期：{detail.docDate}</span>
              <span>需求日期：{detail.requiredDate ?? '-'}</span>
              <span>状态：{REQUISITION_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>申请金额：{money(detail.totalAmount)}</span>
              <span>已转订单号：{detail.convertedOrderNo ?? '-'}</span>
            </Space>
            <Table<RequisitionLineView>
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
