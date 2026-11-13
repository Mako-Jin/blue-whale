// ERP 主生产计划页：列表、新建（期间 + 物料分录）、下达与取消
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
  cancelMpsPlan,
  createMpsPlan,
  getMpsPlan,
  pageMpsPlans,
  releaseMpsPlan,
  type MpsLineView,
  type MpsPlanView,
} from '../api/mfg';
import { listErpItems, type ErpItem } from '../api/erp';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, quantity, scmStatusColor } from '../utils/scm';

/** 计划分录表单值 */
interface MpsLineFormValues {
  itemCode: string;
  plannedQuantity?: number;
  uom?: string;
}

/** 主生产计划表单值 */
interface MpsFormValues {
  period: Dayjs;
  remark?: string;
  lines: MpsLineFormValues[];
}

/** 主生产计划页 */
export default function MpsPage() {
  const [data, setData] = useState<MpsPlanView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<MpsPlanView | null>(null);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<MpsFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMpsPlans({ size: 50, keyword, status });
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
      lines: [{ itemCode: '', plannedQuantity: 1 }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createMpsPlan({
      period: values.period.format('YYYY-MM'),
      remark: values.remark,
      lines: values.lines.map((line) => ({
        itemCode: line.itemCode,
        plannedQuantity: Number(line.plannedQuantity ?? 0),
        uom: line.uom,
      })),
    });
    message.success('主生产计划已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleRelease = async (id: number) => {
    await releaseMpsPlan(id);
    message.success('主生产计划已下达');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelMpsPlan(id);
    message.success('主生产计划已取消');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getMpsPlan(id));
  };

  const columns: TableProps<MpsPlanView>['columns'] = [
    { title: '计划单号', dataIndex: 'planNo', key: 'planNo', width: 180 },
    { title: '计划期间', dataIndex: 'period', key: 'period', width: 110 },
    {
      title: '总计划数量',
      dataIndex: 'totalPlanned',
      key: 'totalPlanned',
      width: 130,
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
          {record.status === 'DRAFT' && hasPermission('sys:erp:mps:release') ? (
            <Popconfirm
              title="下达后分录计划数量将固化，确认继续？"
              onConfirm={() => handleRelease(record.id)}
            >
              <Button type="link" size="small">
                下达
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'DRAFT' && hasPermission('sys:erp:mps:cancel') ? (
            <Popconfirm title="确认取消该主生产计划？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<MpsLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    {
      title: '计划数量',
      dataIndex: 'plannedQuantity',
      key: 'plannedQuantity',
      width: 120,
      render: quantity,
    },
    {
      title: '已下达数量',
      dataIndex: 'releasedQuantity',
      key: 'releasedQuantity',
      width: 120,
      render: quantity,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="计划单号 / 期间"
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
          onClick={() => setStatus('RELEASED')}
          type={status === 'RELEASED' ? 'primary' : 'default'}
        >
          已下达
        </Button>
        <Button
          onClick={() => setStatus('CANCELLED')}
          type={status === 'CANCELLED' ? 'primary' : 'default'}
        >
          已取消
        </Button>
        {hasPermission('sys:erp:mps:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增主生产计划
          </Button>
        ) : null}
      </Space>
      <Table<MpsPlanView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增主生产计划"
        width={880}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="period"
            label="计划期间"
            rules={[{ required: true, message: '请选择计划期间' }]}
          >
            <DatePicker picker="month" format="YYYY-MM" />
          </Form.Item>
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
                      name={[field.name, 'plannedQuantity']}
                      rules={[{ required: true, message: '计划数量' }]}
                    >
                      <InputNumber
                        placeholder="计划数量"
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
                <Button type="dashed" onClick={() => add({ plannedQuantity: 1 })} block>
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
        title={detail ? `主生产计划 ${detail.planNo}` : '主生产计划详情'}
        footer={null}
        width={880}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>期间：{detail.period}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>总计划数量：{quantity(detail.totalPlanned)}</span>
            </Space>
            <Table<MpsLineView>
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
