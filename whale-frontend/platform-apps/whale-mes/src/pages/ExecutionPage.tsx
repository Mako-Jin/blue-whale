// MES 生产执行页：工序任务开工 / 暂停 / 恢复 / 完工 / 报废与扫码报工
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  listMesMasterDataOptions,
  pageMesTasks,
  reportMesProduction,
  runMesTaskAction,
  type MesMasterData,
  type MesReportInput,
  type MesTask,
  type MesTaskAction,
  type MesTaskStatus,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 任务状态文案 */
const TASK_STATUS_LABEL: Record<MesTaskStatus, string> = {
  PLANNED: '待派工',
  DISPATCHED: '已派工',
  IN_PROGRESS: '生产中',
  PAUSED: '已暂停',
  COMPLETED: '已完工',
  SCRAPPED: '已报废',
};

/** 任务状态标签颜色 */
const TASK_STATUS_COLOR: Record<MesTaskStatus, string> = {
  PLANNED: 'default',
  DISPATCHED: 'blue',
  IN_PROGRESS: 'processing',
  PAUSED: 'orange',
  COMPLETED: 'green',
  SCRAPPED: 'red',
};

/** 执行筛选状态：只看可执行任务 */
const EXECUTABLE_STATUS: MesTaskStatus[] = [
  'DISPATCHED',
  'IN_PROGRESS',
  'PAUSED',
  'COMPLETED',
  'SCRAPPED',
];

/** 数量展示 */
function formatQuantity(value?: number | string): string {
  return value === undefined || value === null ? '-' : String(value);
}

/** 报工表单值 */
interface ReportFormValues {
  reportQuantity: number;
  qualifiedQuantity: number;
  defectQuantity: number;
  reporter?: string;
  remark?: string;
  defects?: Array<{ defectCode: string; quantity: number }>;
}

/** 报废表单值 */
interface ScrapFormValues {
  quantity: number;
  remark: string;
}

/** 生产执行页 */
export default function ExecutionPage() {
  const [tasks, setTasks] = useState<MesTask[]>([]);
  const [status, setStatus] = useState<MesTaskStatus | undefined>('DISPATCHED');
  const [loading, setLoading] = useState(false);
  const [defectOptions, setDefectOptions] = useState<MesMasterData[]>([]);
  const [reportTask, setReportTask] = useState<MesTask | null>(null);
  const [scrapTask, setScrapTask] = useState<MesTask | null>(null);
  const [reportForm] = Form.useForm<ReportFormValues>();
  const [scrapForm] = Form.useForm<ScrapFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesTasks({ status });
      setTasks(page.records);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void (async () => {
      setDefectOptions(await listMesMasterDataOptions('defect-code'));
    })();
  }, []);

  const handleAction = async (task: MesTask, action: MesTaskAction) => {
    try {
      await runMesTaskAction(task.id, action);
      message.success(`${TASK_STATUS_LABEL[task.status]}任务已提交：${action}`);
      await load();
    } catch {
      message.error('操作失败：请确认任务当前状态允许该动作');
    }
  };

  const openReport = (task: MesTask) => {
    setReportTask(task);
    reportForm.resetFields();
    reportForm.setFieldsValue({
      reportQuantity: 0,
      qualifiedQuantity: 0,
      defectQuantity: 0,
      defects: [],
    });
  };

  const submitReport = async () => {
    if (!reportTask) return;
    const values = await reportForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      const input: MesReportInput = {
        taskId: reportTask.id,
        reportQuantity: values.reportQuantity,
        qualifiedQuantity: values.qualifiedQuantity,
        defectQuantity: values.defectQuantity,
        reporter: values.reporter || undefined,
        remark: values.remark || undefined,
        defects: values.defects?.filter((item) => item?.defectCode && item.quantity > 0),
      };
      await reportMesProduction(input);
      message.success('报工成功');
      setReportTask(null);
      await load();
    } catch {
      message.error('报工失败：请确认报工数量口径与累计数量不超过已派工');
    }
  };

  const openScrap = (task: MesTask) => {
    setScrapTask(task);
    scrapForm.resetFields();
    scrapForm.setFieldsValue({ quantity: 0, remark: '' });
  };

  const submitScrap = async () => {
    if (!scrapTask) return;
    const values = await scrapForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      await runMesTaskAction(scrapTask.id, 'scrap', {
        quantity: values.quantity,
        remark: values.remark,
      });
      message.success('报废登记成功');
      setScrapTask(null);
      await load();
    } catch {
      message.error('报废失败：请确认报废数量不超过已派工剩余数量');
    }
  };

  const columns: TableProps<MesTask>['columns'] = [
    { title: '任务编号', dataIndex: 'taskNo', key: 'taskNo', width: 190 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    {
      title: '工序',
      key: 'operation',
      width: 160,
      render: (_, record) =>
        `${record.operationCode ?? ''} ${record.operationName ?? ''}`.trim() || '-',
    },
    {
      title: '已派工 / 已完工',
      key: 'quantity',
      width: 150,
      render: (_, record) =>
        `${formatQuantity(record.dispatchedQuantity)} / ${formatQuantity(record.completedQuantity)}`,
    },
    {
      title: '累计报工',
      key: 'reportedQuantity',
      width: 140,
      render: (_, record) =>
        `${formatQuantity(record.reportedQuantity)}（合格 ${formatQuantity(record.qualifiedQuantity)} / 不良 ${formatQuantity(record.defectQuantity)}）`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => {
        const current = value as MesTaskStatus;
        return (
          <Tag color={TASK_STATUS_COLOR[current]}>{TASK_STATUS_LABEL[current] ?? current}</Tag>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_, record) => {
        if (!hasPermission('sys:mes:execution:action')) return null;
        return (
          <Space size="small" wrap>
            {record.status === 'DISPATCHED' ? (
              <Button type="link" size="small" onClick={() => handleAction(record, 'start')}>
                开工
              </Button>
            ) : null}
            {record.status === 'IN_PROGRESS' ? (
              <Button type="link" size="small" onClick={() => handleAction(record, 'pause')}>
                暂停
              </Button>
            ) : null}
            {record.status === 'PAUSED' ? (
              <Button type="link" size="small" onClick={() => handleAction(record, 'resume')}>
                恢复
              </Button>
            ) : null}
            {record.status === 'IN_PROGRESS' || record.status === 'PAUSED' ? (
              <>
                {hasPermission('sys:mes:execution:report') ? (
                  <Button type="link" size="small" onClick={() => openReport(record)}>
                    报工
                  </Button>
                ) : null}
                <Popconfirm
                  title="确认完成任务？完工数量取累计合格数"
                  okText="完工"
                  cancelText="取消"
                  onConfirm={() => handleAction(record, 'complete')}
                >
                  <Button type="link" size="small">
                    完工
                  </Button>
                </Popconfirm>
              </>
            ) : null}
            {record.status === 'DISPATCHED' ||
            record.status === 'IN_PROGRESS' ||
            record.status === 'PAUSED' ? (
              <Button type="link" size="small" danger onClick={() => openScrap(record)}>
                报废
              </Button>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          生产执行
        </Typography.Title>
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 160 }}
          value={status}
          onChange={(value) => setStatus(value as MesTaskStatus | undefined)}
          options={EXECUTABLE_STATUS.map((value) => ({ value, label: TASK_STATUS_LABEL[value] }))}
        />
      </Space>
      <Table<MesTask>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={tasks}
        scroll={{ x: 1300 }}
      />
      <Modal
        open={reportTask !== null}
        title={`报工 - ${reportTask?.taskNo ?? ''}`}
        onOk={submitReport}
        onCancel={() => setReportTask(null)}
        okText="提交"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={reportForm} layout="vertical">
          <Form.Item
            name="reportQuantity"
            label="报工数量"
            rules={[{ required: true, message: '请填写报工数量' }]}
          >
            <InputNumber min={0.0001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="qualifiedQuantity"
            label="合格数量"
            dependencies={['reportQuantity', 'defectQuantity']}
            rules={[
              { required: true, message: '请填写合格数量' },
              ({ getFieldValue }) => ({
                validator: (_, value) => {
                  const report = getFieldValue('reportQuantity') as number;
                  const defect = getFieldValue('defectQuantity') as number;
                  if (!value || !report) return Promise.resolve();
                  return Number(value) + Number(defect || 0) === Number(report)
                    ? Promise.resolve()
                    : Promise.reject(new Error('合格数量 + 不良数量必须等于报工数量'));
                },
              }),
            ]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="defectQuantity"
            label="不良数量"
            dependencies={['reportQuantity', 'qualifiedQuantity']}
            rules={[
              { required: true, message: '请填写不良数量' },
              ({ getFieldValue }) => ({
                validator: (_, value) => {
                  const report = getFieldValue('reportQuantity') as number;
                  const qualified = getFieldValue('qualifiedQuantity') as number;
                  if (!value || !report) return Promise.resolve();
                  return Number(value) + Number(qualified || 0) === Number(report)
                    ? Promise.resolve()
                    : Promise.reject(new Error('合格数量 + 不良数量必须等于报工数量'));
                },
              }),
            ]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reporter" label="报工人">
            <Input maxLength={64} placeholder="工号或姓名" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
          <Form.List name="defects">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" style={{ width: '100%' }}>
                    <Form.Item
                      name={[field.name, 'defectCode']}
                      rules={[{ required: true, message: '请选择缺陷代码' }]}
                    >
                      <Select
                        placeholder="缺陷代码"
                        style={{ width: 180 }}
                        showSearch
                        optionFilterProp="label"
                        options={defectOptions.map((item) => ({
                          value: item.code,
                          label: `${item.code} ${item.name}`.trim(),
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'quantity']}
                      rules={[{ required: true, message: '请填写不良数量' }]}
                    >
                      <InputNumber min={0.0001} placeholder="数量" style={{ width: 120 }} />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} block>
                  添加不良明细
                </Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>
      <Modal
        open={scrapTask !== null}
        title={`报废登记 - ${scrapTask?.taskNo ?? ''}`}
        onOk={submitScrap}
        onCancel={() => setScrapTask(null)}
        okText="提交"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <Form form={scrapForm} layout="vertical">
          <Form.Item
            name="quantity"
            label="报废数量"
            rules={[{ required: true, message: '请填写报废数量' }]}
          >
            <InputNumber min={0.0001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="remark"
            label="报废原因"
            rules={[{ required: true, message: '请填写报废原因' }]}
          >
            <Input.TextArea rows={3} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
