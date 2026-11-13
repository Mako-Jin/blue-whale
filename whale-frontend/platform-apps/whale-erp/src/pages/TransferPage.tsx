// ERP 库存调拨页：列表、新建（跨仓同成本结转）、过账与取消
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
  cancelTransfer,
  createTransfer,
  getTransfer,
  pageTransfers,
  postTransfer,
  type TransferLineView,
  type TransferView,
} from '../api/scm';
import { listErpItems, type ErpItem } from '../api/erp';
import { pageWarehouses, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 调拨分录表单值 */
interface TransferLineFormValues {
  itemCode: string;
  quantity?: number;
  uom?: string;
}

/** 调拨单表单值 */
interface TransferFormValues {
  transferDate: Dayjs;
  fromWarehouseId: number;
  toWarehouseId: number;
  remark?: string;
  lines: TransferLineFormValues[];
}

/** 库存调拨页 */
export default function TransferPage() {
  const [data, setData] = useState<TransferView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<TransferView | null>(null);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<TransferFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageTransfers({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const [warehousePage, itemList] = await Promise.all([
      pageWarehouses({ size: 100 }),
      listErpItems(),
    ]);
    setWarehouses(warehousePage.records);
    setItems(itemList);
    form.resetFields();
    form.setFieldsValue({
      transferDate: dayjs(),
      lines: [{ itemCode: '', quantity: 1 }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createTransfer({
      transferDate: values.transferDate.format('YYYY-MM-DD'),
      fromWarehouseId: values.fromWarehouseId,
      toWarehouseId: values.toWarehouseId,
      remark: values.remark,
      lines: values.lines.map((line) => ({
        itemCode: line.itemCode,
        quantity: Number(line.quantity ?? 0),
        uom: line.uom,
      })),
    });
    message.success('库存调拨单已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handlePost = async (id: number) => {
    await postTransfer(id);
    message.success('调拨已过账，调出仓出库、调入仓入库');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelTransfer(id);
    message.success('调拨单已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getTransfer(id));
  };

  const columns: TableProps<TransferView>['columns'] = [
    { title: '调拨单号', dataIndex: 'docNo', key: 'docNo', width: 180 },
    { title: '调拨日期', dataIndex: 'transferDate', key: 'transferDate', width: 110 },
    { title: '调出仓库', dataIndex: 'fromWarehouseName', key: 'fromWarehouseName' },
    { title: '调入仓库', dataIndex: 'toWarehouseName', key: 'toWarehouseName' },
    {
      title: '总数量',
      dataIndex: 'totalQuantity',
      key: 'totalQuantity',
      width: 110,
      render: quantity,
    },
    { title: '总金额', dataIndex: 'totalAmount', key: 'totalAmount', width: 120, render: money },
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
          {record.status === 'DRAFT' && hasPermission('sys:erp:transfer:post') ? (
            <Popconfirm title="过账后不可撤销，确认继续？" onConfirm={() => handlePost(record.id)}>
              <Button type="link" size="small">
                过账
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'DRAFT' && hasPermission('sys:erp:transfer:cancel') ? (
            <Popconfirm title="确认取消该调拨单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<TransferLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 110, render: quantity },
    { title: '单位成本', dataIndex: 'unitCost', key: 'unitCost', width: 120, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="调拨单号 / 仓库"
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
          onClick={() => setStatus('POSTED')}
          type={status === 'POSTED' ? 'primary' : 'default'}
        >
          已过账
        </Button>
        {hasPermission('sys:erp:transfer:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增调拨单
          </Button>
        ) : null}
      </Space>
      <Table<TransferView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增库存调拨单"
        width={880}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="fromWarehouseId"
              label="调出仓库"
              rules={[{ required: true, message: '请选择调出仓库' }]}
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
            <Form.Item
              name="toWarehouseId"
              label="调入仓库"
              rules={[{ required: true, message: '请选择调入仓库' }]}
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
            <Form.Item name="transferDate" label="调拨日期" rules={[{ required: true }]}>
              <DatePicker />
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
                        style={{ width: 280 }}
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
        title={detail ? `调拨单 ${detail.docNo}` : '调拨单详情'}
        footer={null}
        width={880}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>调出：{detail.fromWarehouseName}</span>
              <span>调入：{detail.toWarehouseName}</span>
              <span>日期：{detail.transferDate}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>数量：{quantity(detail.totalQuantity)}</span>
              <span>金额：{money(detail.totalAmount)}</span>
            </Space>
            <Table<TransferLineView>
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
