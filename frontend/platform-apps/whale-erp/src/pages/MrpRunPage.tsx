// ERP MRP 运算页：需求行录入、执行运算、运行记录、详情与重跑
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
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
  getMrpRun,
  pageMrpRuns,
  rerunMrp,
  runMrp,
  type MrpPlannedOrderView,
  type MrpRunLineView,
  type MrpRunView,
} from '../api/mfg';
import { listErpItems, type ErpItem } from '../api/erp';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, quantity, scmStatusColor } from '../utils/scm';

/** 需求行表单值 */
interface DemandLineFormValues {
  itemCode: string;
  bomNo: string;
  versionNo: string;
  quantity?: number;
  dueDate: Dayjs;
}

/** MRP 运算表单值 */
interface MrpRunFormValues {
  remark?: string;
  lines: DemandLineFormValues[];
}

/** 计划订单建议类型文案 */
const ORDER_TYPE_LABEL: Record<string, string> = {
  PURCHASE: '采购建议',
  PRODUCTION: '生产建议',
};

/** 时间格式化 */
function formatTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

/** MRP 运算页 */
export default function MrpRunPage() {
  const [data, setData] = useState<MrpRunView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<MrpRunView | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<MrpRunFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMrpRuns({ size: 50, keyword, status });
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
      lines: [{ itemCode: '', bomNo: '', versionNo: '', quantity: 1, dueDate: dayjs() }],
    });
    setCreateOpen(true);
  };

  const openDetail = async (id: number) => {
    setDetail(await getMrpRun(id));
    setDetailOpen(true);
  };

  const handleRun = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const result = await runMrp({
      remark: values.remark,
      lines: values.lines.map((line) => ({
        itemCode: line.itemCode,
        bomNo: line.bomNo.trim(),
        versionNo: line.versionNo.trim(),
        quantity: Number(line.quantity ?? 0),
        dueDate: line.dueDate.format('YYYY-MM-DD'),
      })),
    });
    setCreateOpen(false);
    setVersion((value) => value + 1);
    if (result.status === 'FAILED') {
      message.error(`MRP 运算失败：${result.errorMessage ?? '未知原因'}`);
    } else {
      message.success(`MRP 运算完成，生成计划订单 ${result.plannedOrderCount} 条`);
    }
    await openDetail(result.id);
  };

  const handleRerun = async (id: number) => {
    const result = await rerunMrp(id);
    setVersion((value) => value + 1);
    if (result.status === 'FAILED') {
      message.error(`MRP 重跑失败：${result.errorMessage ?? '未知原因'}`);
    } else {
      message.success(`MRP 重跑完成，运行单号 ${result.runNo}`);
    }
    await openDetail(result.id);
  };

  const columns: TableProps<MrpRunView>['columns'] = [
    { title: '运行单号', dataIndex: 'runNo', key: 'runNo', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={scmStatusColor(String(value))}>{SCM_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    { title: '需求行', dataIndex: 'demandLineCount', key: 'demandLineCount', width: 90 },
    { title: 'BOM 快照行', dataIndex: 'snapshotLineCount', key: 'snapshotLineCount', width: 110 },
    { title: '计划订单', dataIndex: 'plannedOrderCount', key: 'plannedOrderCount', width: 100 },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      key: 'startTime',
      width: 180,
      render: formatTime,
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      key: 'endTime',
      width: 180,
      render: formatTime,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openDetail(record.id)}>
            查看
          </Button>
          {record.status !== 'RUNNING' && hasPermission('sys:erp:mrp:run') ? (
            <Popconfirm title="按原需求行重新运算并生成新的运行记录，确认继续？" onConfirm={() => handleRerun(record.id)}>
              <Button type="link" size="small">
                重跑
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<MrpRunLineView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '需求物料', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: 'BOM 编号', dataIndex: 'bomNo', key: 'bomNo', width: 160 },
    { title: 'BOM 版本', dataIndex: 'bomVersion', key: 'bomVersion', width: 110 },
    { title: '需求数量', dataIndex: 'quantity', key: 'quantity', width: 120, render: quantity },
    { title: '需求日期', dataIndex: 'dueDate', key: 'dueDate', width: 120 },
  ];

  const orderColumns: TableProps<MrpPlannedOrderView>['columns'] = [
    { title: '计划订单号', dataIndex: 'orderNo', key: 'orderNo', width: 180 },
    {
      title: '建议类型',
      dataIndex: 'orderType',
      key: 'orderType',
      width: 100,
      render: (value) => (
        <Tag color={value === 'PRODUCTION' ? 'blue' : 'orange'}>
          {ORDER_TYPE_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    {
      title: '物料',
      key: 'item',
      render: (_, record) => `${record.itemCode}${record.itemName ? ` ${record.itemName}` : ''}`,
    },
    { title: '层级', dataIndex: 'itemLevel', key: 'itemLevel', width: 80 },
    { title: '毛需求', dataIndex: 'grossQuantity', key: 'grossQuantity', width: 110, render: quantity },
    {
      title: '可用量',
      dataIndex: 'availableQuantity',
      key: 'availableQuantity',
      width: 110,
      render: quantity,
    },
    { title: '净需求', dataIndex: 'netQuantity', key: 'netQuantity', width: 110, render: quantity },
    { title: '需求日期', dataIndex: 'needDate', key: 'needDate', width: 110 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="运行单号 / 备注"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button onClick={() => setStatus('RUNNING')} type={status === 'RUNNING' ? 'primary' : 'default'}>
          运行中
        </Button>
        <Button onClick={() => setStatus('COMPLETED')} type={status === 'COMPLETED' ? 'primary' : 'default'}>
          已完成
        </Button>
        <Button onClick={() => setStatus('FAILED')} type={status === 'FAILED' ? 'primary' : 'default'}>
          失败
        </Button>
        {hasPermission('sys:erp:mrp:run') ? (
          <Button type="primary" onClick={openCreate}>
            执行 MRP 运算
          </Button>
        ) : null}
      </Space>
      <Table<MrpRunView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="执行 MRP 运算"
        width={960}
        onOk={handleRun}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'itemCode']}
                      rules={[{ required: true, message: '请选择需求物料' }]}
                    >
                      <Select
                        showSearch
                        optionFilterProp="label"
                        placeholder="需求物料"
                        style={{ width: 240 }}
                        options={items.map((item) => ({
                          value: item.code,
                          label: `${item.code} ${item.name}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'bomNo']}
                      rules={[{ required: true, message: '请输入 BOM 编号' }]}
                    >
                      <Input placeholder="PDM BOM 编号" style={{ width: 160 }} maxLength={64} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'versionNo']}
                      rules={[{ required: true, message: '请输入 BOM 版本' }]}
                    >
                      <Input placeholder="BOM 版本" style={{ width: 110 }} maxLength={32} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'quantity']}
                      rules={[{ required: true, message: '需求数量' }]}
                    >
                      <InputNumber placeholder="需求数量" min={0.0001} precision={4} style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'dueDate']}
                      rules={[{ required: true, message: '需求日期' }]}
                    >
                      <DatePicker placeholder="需求日期" />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add({ quantity: 1, dueDate: dayjs() })}
                  block
                >
                  增加需求行
                </Button>
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        title={detail ? `MRP 运行记录 ${detail.runNo}` : 'MRP 运行记录'}
        width={1000}
        onClose={() => setDetailOpen(false)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="状态">
                <Tag color={scmStatusColor(detail.status)}>
                  {SCM_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="备注">{detail.remark ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="需求行">{detail.demandLineCount}</Descriptions.Item>
              <Descriptions.Item label="BOM 快照行">{detail.snapshotLineCount}</Descriptions.Item>
              <Descriptions.Item label="计划订单">{detail.plannedOrderCount}</Descriptions.Item>
              <Descriptions.Item label="耗时">
                {formatTime(detail.startTime)} ~ {formatTime(detail.endTime)}
              </Descriptions.Item>
            </Descriptions>
            {detail.errorMessage ? (
              <Alert type="error" showIcon message="运算失败原因" description={detail.errorMessage} />
            ) : null}
            <Table<MrpRunLineView>
              rowKey="lineNo"
              size="small"
              title={() => '输入需求行'}
              columns={lineColumns}
              dataSource={detail.lines ?? []}
              pagination={false}
            />
            <Table<MrpPlannedOrderView>
              rowKey="id"
              size="small"
              title={() => '计划订单概览'}
              columns={orderColumns}
              dataSource={detail.orders ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
