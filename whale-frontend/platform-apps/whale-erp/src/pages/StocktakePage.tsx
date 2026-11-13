// ERP 库存盘点页：列表、新建（按仓库余额预填账面数量）、过账与取消
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
  cancelStocktake,
  createStocktake,
  getStocktake,
  pageStockBalances,
  pageStocktakes,
  postStocktake,
  type StockBalanceView,
  type StocktakeLineView,
  type StocktakeView,
} from '../api/scm';
import { pageWarehouses, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 盘点录入行：账面数量取库存余额，实盘数量可编辑 */
interface CountedRow {
  itemCode: string;
  itemName?: string;
  bookQuantity: number;
  countedQuantity: number;
}

/** 盘点单表单值 */
interface StocktakeFormValues {
  stocktakeDate: Dayjs;
  warehouseId: number;
  remark?: string;
}

/** 库存盘点页 */
export default function StocktakePage() {
  const [data, setData] = useState<StocktakeView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<StocktakeView | null>(null);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [rows, setRows] = useState<CountedRow[]>([]);
  const [rowsLoading, setRowsLoading] = useState(false);
  const [form] = Form.useForm<StocktakeFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageStocktakes({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const warehousePage = await pageWarehouses({ size: 100 });
    setWarehouses(warehousePage.records);
    setRows([]);
    form.resetFields();
    form.setFieldsValue({ stocktakeDate: dayjs() });
    setCreateOpen(true);
  };

  const handleWarehouseChange = async (warehouseId: number) => {
    setRowsLoading(true);
    try {
      const page = await pageStockBalances({ warehouseId, size: 100 });
      setRows(
        page.records.map((balance: StockBalanceView) => ({
          itemCode: balance.itemCode,
          itemName: balance.itemName,
          bookQuantity: Number(balance.quantity ?? 0),
          countedQuantity: Number(balance.quantity ?? 0),
        })),
      );
    } finally {
      setRowsLoading(false);
    }
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    if (rows.length === 0) {
      message.warning('所选仓库暂无库存余额，请先入库后再盘点');
      return;
    }
    await createStocktake({
      stocktakeDate: values.stocktakeDate.format('YYYY-MM-DD'),
      warehouseId: values.warehouseId,
      remark: values.remark,
      lines: rows.map((row) => ({
        itemCode: row.itemCode,
        countedQuantity: Number(row.countedQuantity ?? 0),
      })),
    });
    message.success('库存盘点单已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handlePost = async (id: number) => {
    await postStocktake(id);
    message.success('盘点已过账，差异已生成出入库流水与凭证');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelStocktake(id);
    message.success('盘点单已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getStocktake(id));
  };

  const columns: TableProps<StocktakeView>['columns'] = [
    { title: '盘点单号', dataIndex: 'docNo', key: 'docNo', width: 180 },
    { title: '盘点日期', dataIndex: 'stocktakeDate', key: 'stocktakeDate', width: 110 },
    { title: '仓库', dataIndex: 'warehouseName', key: 'warehouseName' },
    { title: '盘盈金额', dataIndex: 'gainAmount', key: 'gainAmount', width: 120, render: money },
    { title: '盘亏金额', dataIndex: 'lossAmount', key: 'lossAmount', width: 120, render: money },
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
          {record.status === 'DRAFT' && hasPermission('sys:erp:stocktake:post') ? (
            <Popconfirm
              title="过账后将生成差异流水与凭证，确认继续？"
              onConfirm={() => handlePost(record.id)}
            >
              <Button type="link" size="small">
                过账
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'DRAFT' && hasPermission('sys:erp:stocktake:cancel') ? (
            <Popconfirm title="确认取消该盘点单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const countedColumns: TableProps<CountedRow>['columns'] = [
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    {
      title: '账面数量',
      dataIndex: 'bookQuantity',
      key: 'bookQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: '实盘数量',
      dataIndex: 'countedQuantity',
      key: 'countedQuantity',
      width: 150,
      render: (value, record) => (
        <InputNumber
          min={0}
          precision={4}
          value={Number(value ?? 0)}
          style={{ width: 120 }}
          onChange={(next) =>
            setRows((previous) =>
              previous.map((row) =>
                row.itemCode === record.itemCode
                  ? { ...row, countedQuantity: Number(next ?? 0) }
                  : row,
              ),
            )
          }
        />
      ),
    },
    {
      title: '差异',
      key: 'difference',
      width: 110,
      render: (_, record) => quantity(Number(record.countedQuantity) - Number(record.bookQuantity)),
    },
  ];

  const lineColumns: TableProps<StocktakeLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 80 },
    {
      title: '账面数量',
      dataIndex: 'bookQuantity',
      key: 'bookQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: '实盘数量',
      dataIndex: 'countedQuantity',
      key: 'countedQuantity',
      width: 110,
      render: quantity,
    },
    { title: '差异', dataIndex: 'difference', key: 'difference', width: 100, render: quantity },
    { title: '单位成本', dataIndex: 'unitCost', key: 'unitCost', width: 110, render: money },
    { title: '金额', dataIndex: 'amount', key: 'amount', width: 110, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="盘点单号 / 仓库"
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
        {hasPermission('sys:erp:stocktake:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增盘点单
          </Button>
        ) : null}
      </Space>
      <Table<StocktakeView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增库存盘点单"
        width={880}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="warehouseId"
              label="盘点仓库"
              rules={[{ required: true, message: '请选择仓库' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 220 }}
                onChange={handleWarehouseChange}
                options={warehouses.map((item) => ({
                  value: item.id,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="stocktakeDate" label="盘点日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item label="盘点明细（按仓库当前结存预填账面数量）">
            <Table<CountedRow>
              rowKey="itemCode"
              size="small"
              loading={rowsLoading}
              columns={countedColumns}
              dataSource={rows}
              pagination={false}
              locale={{ emptyText: '请选择仓库以加载结存明细' }}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={detail !== null}
        title={detail ? `盘点单 ${detail.docNo}` : '盘点单详情'}
        footer={null}
        width={980}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>仓库：{detail.warehouseName}</span>
              <span>日期：{detail.stocktakeDate}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>盘盈：{money(detail.gainAmount)}</span>
              <span>盘亏：{money(detail.lossAmount)}</span>
            </Space>
            <Table<StocktakeLineView>
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
