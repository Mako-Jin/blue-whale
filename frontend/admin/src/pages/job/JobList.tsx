// 定时任务管理页 - 任务列表（分页 / 新增 / 编辑 / 启停 / 立即执行 / 删除）与执行日志（分页）
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  changeJobStatus,
  createJob,
  deleteJob,
  getJobDependencies,
  pageJobAlert,
  pageJobInfo,
  pageJobLog,
  runJob,
  setJobDependencies,
  updateJob,
  type JobAlertVO,
  type JobDependencyVO,
  type JobInfoVO,
  type JobLogVO,
  type JobPayload,
} from '../../api/job';

interface JobFormValues {
  jobName: string;
  jobGroup?: string;
  cronExpression: string;
  handlerBean: string;
  handlerParam?: string;
  misfirePolicy?: string;
  maxRetry?: number;
  retryIntervalSeconds?: number;
  shardTotal?: number;
  status?: number;
  remark?: string;
  /** 上游依赖任务 ID（仅编辑场景，任务本身字段之外单独提交） */
  upstreamJobIds?: number[];
}

type JobQuery = { keyword?: string; status?: number };
type JobLogQuery = { jobId?: number; status?: number };
type JobAlertQuery = { jobId?: number };

const MISFIRE_OPTIONS = [
  { value: 'SMART', label: 'SMART（智能补跑）' },
  { value: 'FIRE_ONCE', label: 'FIRE_ONCE（补跑一次）' },
  { value: 'DO_NOTHING', label: 'DO_NOTHING（忽略）' },
];

const JOB_STATUS_OPTIONS = [
  { value: 1, label: '运行中' },
  { value: 0, label: '已停止' },
];

const LOG_STATUS_OPTIONS = [
  { value: 1, label: '成功' },
  { value: 0, label: '执行中' },
  { value: -1, label: '失败' },
];

const TRIGGER_TYPE_LABELS: Record<string, string> = {
  CRON: '定时触发',
  MANUAL: '手动触发',
  CHAIN: '链式触发',
};

const ALERT_LEVEL_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARN: 'warning',
  WARNING: 'warning',
  ERROR: 'error',
  CRITICAL: 'error',
};

/** 日志状态标签：1 成功 / 0 执行中 / -1 失败 */
function renderLogStatus(value: number | undefined, errorMsg?: string) {
  if (value === 1) {
    return <Tag color="success">成功</Tag>;
  }
  if (value === 0) {
    return <Tag color="processing">执行中</Tag>;
  }
  return (
    <Tooltip title={errorMsg ?? '执行失败'}>
      <Tag color="error">失败</Tag>
    </Tooltip>
  );
}

/** 告警状态标签：1 成功 / -1 失败 / 0 待发 */
function renderAlertStatus(value: number | undefined) {
  if (value === 1) {
    return <Tag color="success">成功</Tag>;
  }
  if (value === -1) {
    return <Tag color="error">失败</Tag>;
  }
  return <Tag>待发</Tag>;
}

const alertColumns: TableProps<JobAlertVO>['columns'] = [
  {
    title: '任务',
    dataIndex: 'jobName',
    width: 180,
    render: (value: string | undefined, row) =>
      value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
  },
  {
    title: '级别',
    dataIndex: 'level',
    width: 100,
    render: (value: string | undefined) =>
      value ? <Tag color={ALERT_LEVEL_COLOR[value.toUpperCase()] ?? 'default'}>{value}</Tag> : '-',
  },
  {
    title: '渠道',
    dataIndex: 'channel',
    width: 120,
    render: (value: string | undefined) => value ?? '-',
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (value: number | undefined) => renderAlertStatus(value),
  },
  {
    title: '错误摘要',
    dataIndex: 'errorMsg',
    ellipsis: true,
    render: (value: string | undefined, row) => value ?? row.message ?? '-',
  },
  {
    title: '时间',
    dataIndex: 'createTime',
    width: 180,
    render: (value: string | undefined) => value ?? '-',
  },
];

export default function JobList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const jobTableRef = useRef<ProTableRef>(null);
  const logTableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [logJobId, setLogJobId] = useState<number | undefined>(undefined);
  const [logStatus, setLogStatus] = useState<number | undefined>(undefined);
  const [alertJobId, setAlertJobId] = useState<number | undefined>(undefined);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<JobInfoVO | null>(null);
  const [editorInitial, setEditorInitial] = useState<Partial<JobFormValues> | undefined>(undefined);
  const [dependency, setDependency] = useState<JobDependencyVO | null>(null);
  const [dependencyLoading, setDependencyLoading] = useState(false);
  const [jobOptions, setJobOptions] = useState<{ value: number; label: string }[]>([]);
  const [editorForm] = Form.useForm<JobFormValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const openEditor = (record: JobInfoVO | null) => {
    setEditRecord(record);
    setDependency(null);
    setDependencyLoading(false);
    setEditorInitial(
      record
        ? {
            ...record,
            maxRetry: record.maxRetry ?? 0,
            retryIntervalSeconds: record.retryIntervalSeconds ?? 60,
            shardTotal: record.shardTotal ?? 1,
          }
        : undefined,
    );
    setEditorOpen(true);
  };

  // 编辑场景：回填上游依赖，并加载候选任务（排除自身）
  useEffect(() => {
    if (!editorOpen || !editRecord) {
      return;
    }
    const jobId = editRecord.id;
    let cancelled = false;
    setDependencyLoading(true);
    Promise.all([getJobDependencies(jobId), pageJobInfo({ current: 1, size: 200 })])
      .then(([dependencyVO, result]) => {
        if (cancelled) {
          return;
        }
        setDependency(dependencyVO);
        editorForm.setFieldsValue({ upstreamJobIds: dependencyVO.upstreamJobIds ?? [] });
        setJobOptions(
          result.records
            .filter((record) => record.id !== jobId)
            .map((record) => ({ value: record.id, label: `${record.jobName}（#${record.id}）` })),
        );
      })
      .catch((error) => {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '依赖信息加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setDependencyLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [editorOpen, editRecord, editorForm, message]);

  const handleToggleStatus = async (row: JobInfoVO, next: number) => {
    try {
      await changeJobStatus(row.id, next);
      message.success(next === 1 ? '任务已启用' : '任务已停用');
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, '状态变更失败');
    }
  };

  const handleRun = async (row: JobInfoVO) => {
    try {
      await runJob(row.id);
      message.success('任务已触发执行');
      logTableRef.current?.reload();
    } catch (error) {
      showError(error, '触发执行失败');
    }
  };

  const handleDelete = async (row: JobInfoVO) => {
    try {
      await deleteJob(row.id);
      message.success('任务已删除');
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const handleSubmit = async (values: JobFormValues) => {
    const payload: JobPayload = {
      jobName: values.jobName,
      jobGroup: values.jobGroup || undefined,
      cronExpression: values.cronExpression,
      handlerBean: values.handlerBean,
      handlerParam: values.handlerParam || undefined,
      misfirePolicy: values.misfirePolicy,
      maxRetry: values.maxRetry ?? 0,
      retryIntervalSeconds: values.retryIntervalSeconds ?? 60,
      shardTotal: values.shardTotal ?? 1,
      status: values.status,
      remark: values.remark || undefined,
    };
    try {
      if (editRecord) {
        await updateJob(editRecord.id, payload);
        // 保存任务后全量替换上游依赖
        await setJobDependencies(editRecord.id, values.upstreamJobIds ?? []);
        message.success('任务已更新');
      } else {
        await createJob(payload);
        message.success('任务已创建');
      }
      setEditorOpen(false);
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const jobColumns: TableProps<JobInfoVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '任务名称', dataIndex: 'jobName', width: 160 },
    {
      title: '分组',
      dataIndex: 'jobGroup',
      width: 110,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: 'Cron', dataIndex: 'cronExpression', width: 150 },
    { title: '执行器', dataIndex: 'handlerBean', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has('sys:job:edit')}
          onChange={(checked) => void handleToggleStatus(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: '下次执行时间',
      dataIndex: 'nextFireTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton perm="sys:job:edit" type="link" size="small" onClick={() => openEditor(row)}>
            编辑
          </AuthButton>
          {has('sys:job:run') && (
            <Popconfirm title="确认立即执行该任务？" onConfirm={() => void handleRun(row)}>
              <Button type="link" size="small">
                立即执行
              </Button>
            </Popconfirm>
          )}
          {has('sys:job:delete') && (
            <Popconfirm title="确认删除该任务？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const logColumns: TableProps<JobLogVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '任务ID', dataIndex: 'jobId', width: 100 },
    {
      title: '任务名称',
      dataIndex: 'jobName',
      width: 160,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '执行器',
      dataIndex: 'handlerBean',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => renderLogStatus(value, row.errorMsg),
    },
    {
      title: '触发方式',
      dataIndex: 'triggerType',
      width: 110,
      render: (value: string | undefined) => (value ? (TRIGGER_TYPE_LABELS[value] ?? value) : '-'),
    },
    {
      title: '批次',
      dataIndex: 'runId',
      width: 180,
      render: (value: string | undefined) =>
        value ? (
          <Tooltip title={value}>
            <Typography.Text
              ellipsis
              style={{ maxWidth: 150, display: 'inline-block', verticalAlign: 'bottom' }}
            >
              {value}
            </Typography.Text>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '结果',
      dataIndex: 'result',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
  ];

  return (
    <>
      <Tabs
        defaultActiveKey="job"
        items={[
          {
            key: 'job',
            label: '任务列表',
            children: (
              <ProTable<JobInfoVO, JobQuery>
                ref={jobTableRef}
                rowKey="id"
                title="任务列表"
                columns={jobColumns}
                request={pageJobInfo}
                params={{ keyword, status }}
                onError={(error) => showError(error, '任务列表加载失败')}
                search={
                  <SearchForm
                    items={[
                      {
                        name: 'keyword',
                        label: '关键字',
                        element: <Input allowClear placeholder="任务名称" />,
                      },
                      {
                        name: 'status',
                        label: '状态',
                        element: (
                          <Select
                            allowClear
                            options={JOB_STATUS_OPTIONS}
                            placeholder="全部"
                            style={{ width: 140 }}
                          />
                        ),
                      },
                    ]}
                    onSearch={(values) => {
                      setKeyword((values.keyword as string) || undefined);
                      setStatus(values.status as number | undefined);
                    }}
                    onReset={() => {
                      setKeyword(undefined);
                      setStatus(undefined);
                    }}
                  />
                }
                toolbar={
                  <AuthButton perm="sys:job:add" type="primary" onClick={() => openEditor(null)}>
                    新增任务
                  </AuthButton>
                }
              />
            ),
          },
          {
            key: 'log',
            label: '执行日志',
            children: (
              <ProTable<JobLogVO, JobLogQuery>
                ref={logTableRef}
                rowKey="id"
                title="执行日志"
                columns={logColumns}
                request={pageJobLog}
                params={{ jobId: logJobId, status: logStatus }}
                onError={(error) => showError(error, '执行日志加载失败')}
                search={
                  <SearchForm
                    items={[
                      {
                        name: 'jobId',
                        label: '任务ID',
                        element: (
                          <InputNumber min={1} placeholder="任务ID" style={{ width: 140 }} />
                        ),
                      },
                      {
                        name: 'status',
                        label: '状态',
                        element: (
                          <Select
                            allowClear
                            options={LOG_STATUS_OPTIONS}
                            placeholder="全部"
                            style={{ width: 140 }}
                          />
                        ),
                      },
                    ]}
                    onSearch={(values) => {
                      const rawJobId = values.jobId as number | null | undefined;
                      setLogJobId(
                        rawJobId === undefined || rawJobId === null ? undefined : Number(rawJobId),
                      );
                      setLogStatus(values.status as number | undefined);
                    }}
                    onReset={() => {
                      setLogJobId(undefined);
                      setLogStatus(undefined);
                    }}
                  />
                }
              />
            ),
          },
          {
            key: 'alert',
            label: '告警记录',
            children: (
              <ProTable<JobAlertVO, JobAlertQuery>
                rowKey="id"
                title="告警记录"
                columns={alertColumns}
                request={pageJobAlert}
                params={{ jobId: alertJobId }}
                onError={(error) => showError(error, '告警记录加载失败')}
                search={
                  <SearchForm
                    items={[
                      {
                        name: 'jobId',
                        label: '任务ID',
                        element: (
                          <InputNumber min={1} placeholder="任务ID" style={{ width: 140 }} />
                        ),
                      },
                    ]}
                    onSearch={(values) => {
                      const rawJobId = values.jobId as number | null | undefined;
                      setAlertJobId(
                        rawJobId === undefined || rawJobId === null ? undefined : Number(rawJobId),
                      );
                    }}
                    onReset={() => setAlertJobId(undefined)}
                  />
                }
              />
            ),
          },
        ]}
      />

      <FormModal<JobFormValues>
        open={editorOpen}
        title={editRecord ? `编辑任务：${editRecord.jobName}` : '新增任务'}
        form={editorForm}
        initialValues={editorInitial}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="jobName"
          label="任务名称"
          rules={[{ required: true, message: '请输入任务名称' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="jobGroup" label="任务分组">
          <Input placeholder="留空则为 DEFAULT" />
        </Form.Item>
        <Form.Item
          name="cronExpression"
          label="Cron 表达式"
          rules={[{ required: true, message: '请输入 Cron 表达式' }]}
        >
          <Input placeholder="如 0 0/5 * * * ?" />
        </Form.Item>
        <Form.Item
          name="handlerBean"
          label="执行器 Bean"
          rules={[{ required: true, message: '请输入执行器 Bean 名称' }]}
        >
          <Input placeholder="如 demoJobHandler" />
        </Form.Item>
        <Form.Item name="handlerParam" label="执行器参数">
          <Input />
        </Form.Item>
        <Form.Item name="misfirePolicy" label="错过执行策略" initialValue="SMART">
          <Select options={MISFIRE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="maxRetry"
          label="失败重试次数"
          initialValue={0}
          extra="0 表示不重试，最多 5 次"
        >
          <InputNumber min={0} max={5} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="retryIntervalSeconds"
          label="重试间隔（秒）"
          initialValue={60}
          extra="两次重试之间的等待时间"
        >
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="shardTotal"
          label="分片总数"
          initialValue={1}
          extra="1 表示不分片，最多 16 片"
        >
          <InputNumber min={1} max={16} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        {editRecord && (
          <Form.Item name="upstreamJobIds" label="上游依赖">
            <Select
              mode="multiple"
              allowClear
              showSearch
              optionFilterProp="label"
              loading={dependencyLoading}
              options={jobOptions}
              placeholder="选择前置任务，可多选"
            />
          </Form.Item>
        )}
        {editRecord && (dependency?.downstreamJobIds.length ?? 0) > 0 && (
          <Form.Item label="下游依赖">
            <Typography.Text type="secondary">
              已被 {dependency?.downstreamJobIds.length} 个任务依赖
            </Typography.Text>
          </Form.Item>
        )}
        <Form.Item name="status" label="状态" initialValue={0}>
          <Select options={JOB_STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} />
        </Form.Item>
      </FormModal>
    </>
  );
}
