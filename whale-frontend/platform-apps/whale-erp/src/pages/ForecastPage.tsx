// ERP 需求预测页：列表、新建（期间 + 物料分录）、发布与取消
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
  cancelForecast,
  createForecast,
  getForecast,
  pageForecasts,
  publishForecast,
  type ForecastLineView,
  type ForecastView,
} from '../api/mfg';
import { listErpItems, type ErpItem } from '../api/erp';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, quantity, scmStatusColor } from '../utils/scm';

/** 预测分录表单值 */
interface ForecastLineFormValues {
  itemCode: string;
  quantity?: number;
  uom?: string;
}

/** 预测批次表单值 */
interface ForecastFormValues {
  period: Dayjs;
  version?: number;
  remark?: string;
  lines: ForecastLineFormValues[];
}

/** 需求预测页 */
export default function ForecastPage() {
  const [data, setData] = useState<ForecastView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<ForecastView | null>(null);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<ForecastFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageForecasts({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    setItems(await listErpItems());
    form.resetFields();
    form.setFieldsValue({
      period: dayjs(),
      version: 1,
      lines: [{ itemCode: '', quantity: 1 }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createForecast({
      period: values.period.format('YYYY-MM'),
      version: values.version,
      remark: values.remark,
      lines: values.lines.map((line) => ({
        itemCode: line.itemCode,
        quantity: Number(line.quantity ?? 0),
        uom: line.uom,
      })),
    });
    message.success('需求预测已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handlePublish = async (id: number) => {
    await publishForecast(id);
    message.success('需求预测已发布');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelForecast(id);
    message.success('需求预测已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getForecast(id));
  };

  const columns: TableProps<ForecastView>['columns'] = [
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 180 },
    { title: '预测期间', dataIndex: 'period', key: 'period', width: 110 },
    { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
    {
      title: '总数量',
      dataIndex: 'totalQuantity',
      key: 'totalQuantity',
      width: 120,
      render: quantity,
    },
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
      width: 220,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:forecast:publish') ? (
            <Popconfirm
              title="发布后可用于主生产计划，确认继续？"
              onConfirm={() => handlePublish(record.id)}
            >
              <Button type="link" size="small">
                发布
              </Button>
            </Popconfirm>
          ) : null}
          {(record.status === 'DRAFT' || record.status === 'PUBLISHED') &&
          hasPermission('sys:erp:forecast:cancel') ? (
            <Popconfirm title="确认取消该预测批次？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<ForecastLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    { title: '预测数量', dataIndex: 'quantity', key: 'quantity', width: 120, render: quantity },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="批次号 / 期间"
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
          onClick={() => setStatus('PUBLISHED')}
          type={status === 'PUBLISHED' ? 'primary' : 'default'}
        >
          已发布
        </Button>
        <Button
          onClick={() => setStatus('CANCELLED')}
          type={status === 'CANCELLED' ? 'primary' : 'default'}
        >
          已取消
        </Button>
        {hasPermission('sys:erp:forecast:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增需求预测
          </Button>
        ) : null}
      </Space>
      <Table<ForecastView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增需求预测"
        width={880}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item
              name="period"
              label="预测期间"
              rules={[{ required: true, message: '请选择预测期间' }]}
            >
              <DatePicker picker="month" format="YYYY-MM" />
            </Form.Item>
            <Form.Item name="version" label="版本">
              <InputNumber min={1} precision={0} style={{ width: 120 }} />
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
        title={detail ? `需求预测 ${detail.batchNo}` : '需求预测详情'}
        footer={null}
        width={880}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>期间：{detail.period}</span>
              <span>版本：{detail.version}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>总数量：{quantity(detail.totalQuantity)}</span>
            </Space>
            <Table<ForecastLineView>
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
