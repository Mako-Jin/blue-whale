// 定时任务管理页 - 容器：任务列表 / 执行日志 / 告警记录 / 依赖拓扑四个页签的状态与操作处理。
// 列定义见 columns.tsx，弹窗视图见 JobEditorModal.tsx / JobImportModal.tsx，拓扑见 JobDagPanel.tsx。
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import { App, Button, Form, Input, InputNumber, Popconfirm, Select, Space, Tabs } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  batchChangeJobStatus,
  batchDeleteJobs,
  changeJobStatus,
  createJob,
  deleteJob,
  downloadJobExport,
  getJobDependencies,
  pageJobAlert,
  pageJobInfo,
  pageJobLog,
  runJob,
  setJobDependencies,
  updateJob,
  type JobAlertVO,
  type JobBatchResultVO,
  type JobDependencyVO,
  type JobInfoVO,
  type JobLogVO,
  type JobPayload,
} from '../../api/job';
import { alertColumns, buildJobColumns, logColumns } from './columns';
import {
  ALERT_CHANNEL_OPTIONS,
  ALERT_LEVEL_OPTIONS,
  JOB_STATUS_OPTIONS,
  LOG_STATUS_OPTIONS,
  type JobAlertQuery,
  type JobFormValues,
  type JobLogQuery,
  type JobQuery,
} from './constants';
import { JobDagPanel } from './JobDagPanel';
import { JobEditorModal } from './JobEditorModal';
import { JobImportModal } from './JobImportModal';

export default function JobList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const jobTableRef = useRef<ProTableRef>(null);
  const logTableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [logJobId, setLogJobId] = useState<string | undefined>(undefined);
  const [logStatus, setLogStatus] = useState<number | undefined>(undefined);
  const [alertJobId, setAlertJobId] = useState<string | undefined>(undefined);
  const [alertLevel, setAlertLevel] = useState<string | undefined>(undefined);
  const [alertChannel, setAlertChannel] = useState<string | undefined>(undefined);

  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [batchRunning, setBatchRunning] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<JobInfoVO | null>(null);
  const [editorInitial, setEditorInitial] = useState<Partial<JobFormValues> | undefined>(undefined);
  const [dependency, setDependency] = useState<JobDependencyVO | null>(null);
  const [dependencyLoading, setDependencyLoading] = useState(false);
  const [jobOptions, setJobOptions] = useState<{ value: string; label: string }[]>([]);
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

  /** 批量接口整体 200，失败明细在体内：成功提示 N 条，失败附前 3 条原因 */
  const reportBatch = (action: string, result: JobBatchResultVO) => {
    if (result.failures.length === 0) {
      message.success(`${action}成功 ${result.successCount} 条`);
      return;
    }
    const detail = result.failures
      .slice(0, 3)
      .map((failure) => `#${failure.id} ${failure.reason}`)
      .join('；');
    message.warning(
      `${action}：成功 ${result.successCount} 条，失败 ${result.failures.length} 条（${detail}）`,
    );
  };

  const handleBatchStatus = async (next: number) => {
    if (selectedRowKeys.length === 0) {
      return;
    }
    setBatchRunning(true);
    try {
      const result = await batchChangeJobStatus(selectedRowKeys, next);
      reportBatch(next === 1 ? '批量启用' : '批量停用', result);
      setSelectedRowKeys([]);
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, '批量启停失败');
    } finally {
      setBatchRunning(false);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      return;
    }
    setBatchRunning(true);
    try {
      const result = await batchDeleteJobs(selectedRowKeys);
      reportBatch('批量删除', result);
      setSelectedRowKeys([]);
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, '批量删除失败');
    } finally {
      setBatchRunning(false);
    }
  };

  /** 导出为 JSON 附件：仅导出选中时带 jobIds，否则导出全部任务 */
  const handleExport = async (onlySelected: boolean) => {
    if (onlySelected && selectedRowKeys.length === 0) {
      return;
    }
    try {
      await downloadJobExport({ jobIds: onlySelected ? selectedRowKeys : undefined });
      message.success('导出文件已生成');
    } catch (error) {
      showError(error, '导出失败');
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

  const jobColumns = buildJobColumns({
    has,
    onEdit: openEditor,
    onRun: (row) => void handleRun(row),
    onDelete: (row) => void handleDelete(row),
    onToggle: (row, next) => void handleToggleStatus(row, next),
  });

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
                rowSelection={{
                  selectedRowKeys,
                  onChange: (keys) => setSelectedRowKeys(keys as string[]),
                }}
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
                  <Space wrap>
                    {has('sys:job:edit') && (
                      <>
                        <Button
                          disabled={selectedRowKeys.length === 0}
                          loading={batchRunning}
                          onClick={() => void handleBatchStatus(1)}
                        >
                          批量启用
                        </Button>
                        <Button
                          disabled={selectedRowKeys.length === 0}
                          loading={batchRunning}
                          onClick={() => void handleBatchStatus(0)}
                        >
                          批量停用
                        </Button>
                      </>
                    )}
                    {has('sys:job:delete') && (
                      <Popconfirm
                        title={`确认删除选中的 ${selectedRowKeys.length} 个任务？`}
                        disabled={selectedRowKeys.length === 0}
                        onConfirm={() => void handleBatchDelete()}
                      >
                        <Button danger disabled={selectedRowKeys.length === 0} loading={batchRunning}>
                          批量删除
                        </Button>
                      </Popconfirm>
                    )}
                    {has('sys:job:list') && (
                      <>
                        <Button
                          disabled={selectedRowKeys.length === 0}
                          onClick={() => void handleExport(true)}
                        >
                          导出选中{selectedRowKeys.length > 0 ? `（${selectedRowKeys.length}）` : ''}
                        </Button>
                        <Button onClick={() => void handleExport(false)}>导出全部</Button>
                      </>
                    )}
                    <AuthButton perm="sys:job:add" onClick={() => setImportOpen(true)}>
                      导入
                    </AuthButton>
                    <AuthButton perm="sys:job:add" type="primary" onClick={() => openEditor(null)}>
                      新增任务
                    </AuthButton>
                  </Space>
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
                        rawJobId === undefined || rawJobId === null ? undefined : String(rawJobId),
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
                params={{ jobId: alertJobId, level: alertLevel, channel: alertChannel }}
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
                      {
                        name: 'level',
                        label: '级别',
                        element: (
                          <Select
                            allowClear
                            options={ALERT_LEVEL_OPTIONS}
                            placeholder="全部"
                            style={{ width: 160 }}
                          />
                        ),
                      },
                      {
                        name: 'channel',
                        label: '通道',
                        element: (
                          <Select
                            allowClear
                            options={ALERT_CHANNEL_OPTIONS}
                            placeholder="全部"
                            style={{ width: 160 }}
                          />
                        ),
                      },
                    ]}
                    onSearch={(values) => {
                      const rawJobId = values.jobId as number | null | undefined;
                      setAlertJobId(
                        rawJobId === undefined || rawJobId === null ? undefined : String(rawJobId),
                      );
                      setAlertLevel((values.level as string) || undefined);
                      setAlertChannel((values.channel as string) || undefined);
                    }}
                    onReset={() => {
                      setAlertJobId(undefined);
                      setAlertLevel(undefined);
                      setAlertChannel(undefined);
                    }}
                  />
                }
              />
            ),
          },
          {
            key: 'dag',
            label: '依赖拓扑',
            children: <JobDagPanel />,
          },
        ]}
      />

      <JobEditorModal
        open={editorOpen}
        editRecord={editRecord}
        initial={editorInitial}
        form={editorForm}
        dependency={dependency}
        dependencyLoading={dependencyLoading}
        jobOptions={jobOptions}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      />

      <JobImportModal
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        onImported={() => jobTableRef.current?.reload()}
      />
    </>
  );
}
