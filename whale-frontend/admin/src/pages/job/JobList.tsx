// 定时任务管理页 - 容器：任务列表 / 执行日志 / 告警记录 / 依赖拓扑 / 执行统计五个页签的状态与操作处理。
// 列定义见 columns.tsx，弹窗视图见 JobEditorModal.tsx / JobImportModal.tsx，拓扑与统计见 JobDagPanel.tsx / JobStatsPanel.tsx。
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
import { useI18n } from '../../i18n';
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
import { buildAlertColumns, buildJobColumns, buildLogColumns } from './columns';
import {
  buildAlertChannelOptions,
  buildAlertLevelOptions,
  buildJobStatusOptions,
  buildLogStatusOptions,
  type JobAlertQuery,
  type JobFormValues,
  type JobLogQuery,
  type JobQuery,
} from './constants';
import { JobDagPanel } from './JobDagPanel';
import { JobEditorModal } from './JobEditorModal';
import { JobImportModal } from './JobImportModal';
import { JobStatsPanel } from './JobStatsPanel';

export default function JobList() {
  const { message } = App.useApp();
  const { t } = useI18n();
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
          message.error(
            error instanceof Error ? error.message : t('job.list.msg.dependencyFailed'),
          );
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
  }, [editorOpen, editRecord, editorForm, message, t]);

  const handleToggleStatus = async (row: JobInfoVO, next: number) => {
    try {
      await changeJobStatus(row.id, next);
      message.success(next === 1 ? t('job.list.msg.enabled') : t('job.list.msg.disabled'));
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, t('job.list.msg.statusFailed'));
    }
  };

  const handleRun = async (row: JobInfoVO) => {
    try {
      await runJob(row.id);
      message.success(t('job.list.msg.triggered'));
      logTableRef.current?.reload();
    } catch (error) {
      showError(error, t('job.list.msg.runFailed'));
    }
  };

  const handleDelete = async (row: JobInfoVO) => {
    try {
      await deleteJob(row.id);
      message.success(t('job.list.msg.deleted'));
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, t('job.list.msg.deleteFailed'));
    }
  };

  /** 批量接口整体 200，失败明细在体内：成功提示 N 条，失败附前 3 条原因 */
  const reportBatch = (action: string, result: JobBatchResultVO) => {
    if (result.failures.length === 0) {
      message.success(t('job.list.msg.batchAllSuccess', { action, count: result.successCount }));
      return;
    }
    const detail = result.failures
      .slice(0, 3)
      .map((failure) => `#${failure.id} ${failure.reason}`)
      .join(t('job.list.msg.batchSeparator'));
    message.warning(
      t('job.list.msg.batchPartial', {
        action,
        success: result.successCount,
        failed: result.failures.length,
        detail,
      }),
    );
  };

  const handleBatchStatus = async (next: number) => {
    if (selectedRowKeys.length === 0) {
      return;
    }
    setBatchRunning(true);
    try {
      const result = await batchChangeJobStatus(selectedRowKeys, next);
      reportBatch(next === 1 ? t('job.list.batch.enable') : t('job.list.batch.disable'), result);
      setSelectedRowKeys([]);
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, t('job.list.msg.batchToggleFailed'));
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
      reportBatch(t('job.list.batch.delete'), result);
      setSelectedRowKeys([]);
      jobTableRef.current?.reload();
    } catch (error) {
      showError(error, t('job.list.msg.batchDeleteFailed'));
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
      message.success(t('job.list.msg.exported'));
    } catch (error) {
      showError(error, t('job.list.msg.exportFailed'));
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
        message.success(t('job.list.msg.updated'));
      } else {
        await createJob(payload);
        message.success(t('job.list.msg.created'));
      }
      setEditorOpen(false);
      jobTableRef.current?.reload();
    } catch (error) {
      showError(
        error,
        editRecord ? t('job.list.msg.updateFailed') : t('job.list.msg.createFailed'),
      );
      throw error;
    }
  };

  const jobColumns = buildJobColumns({
    t,
    has,
    onEdit: openEditor,
    onRun: (row) => void handleRun(row),
    onDelete: (row) => void handleDelete(row),
    onToggle: (row, next) => void handleToggleStatus(row, next),
  });
  const logColumns = buildLogColumns(t);
  const alertColumns = buildAlertColumns(t);

  return (
    <>
      <Tabs
        defaultActiveKey="job"
        items={[
          {
            key: 'job',
            label: t('job.tab.jobs'),
            children: (
              <ProTable<JobInfoVO, JobQuery>
                ref={jobTableRef}
                rowKey="id"
                title={t('job.tab.jobs')}
                columns={jobColumns}
                request={pageJobInfo}
                params={{ keyword, status }}
                rowSelection={{
                  selectedRowKeys,
                  onChange: (keys) => setSelectedRowKeys(keys as string[]),
                }}
                onError={(error) => showError(error, t('job.list.msg.loadFailed'))}
                search={
                  <SearchForm
                    items={[
                      {
                        name: 'keyword',
                        label: t('job.list.search.keyword'),
                        element: (
                          <Input allowClear placeholder={t('job.list.search.keywordPlaceholder')} />
                        ),
                      },
                      {
                        name: 'status',
                        label: t('job.list.search.status'),
                        element: (
                          <Select
                            allowClear
                            options={buildJobStatusOptions(t)}
                            placeholder={t('job.list.search.all')}
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
                          {t('job.list.batch.enable')}
                        </Button>
                        <Button
                          disabled={selectedRowKeys.length === 0}
                          loading={batchRunning}
                          onClick={() => void handleBatchStatus(0)}
                        >
                          {t('job.list.batch.disable')}
                        </Button>
                      </>
                    )}
                    {has('sys:job:delete') && (
                      <Popconfirm
                        title={t('job.list.confirm.batchDelete', {
                          count: selectedRowKeys.length,
                        })}
                        disabled={selectedRowKeys.length === 0}
                        onConfirm={() => void handleBatchDelete()}
                      >
                        <Button
                          danger
                          disabled={selectedRowKeys.length === 0}
                          loading={batchRunning}
                        >
                          {t('job.list.batch.delete')}
                        </Button>
                      </Popconfirm>
                    )}
                    {has('sys:job:list') && (
                      <>
                        <Button
                          disabled={selectedRowKeys.length === 0}
                          onClick={() => void handleExport(true)}
                        >
                          {t('job.list.action.exportSelected')}
                          {selectedRowKeys.length > 0
                            ? t('job.list.action.exportSelectedCount', {
                                count: selectedRowKeys.length,
                              })
                            : ''}
                        </Button>
                        <Button onClick={() => void handleExport(false)}>
                          {t('job.list.action.exportAll')}
                        </Button>
                      </>
                    )}
                    <AuthButton perm="sys:job:add" onClick={() => setImportOpen(true)}>
                      {t('job.list.action.import')}
                    </AuthButton>
                    <AuthButton perm="sys:job:add" type="primary" onClick={() => openEditor(null)}>
                      {t('job.list.action.create')}
                    </AuthButton>
                  </Space>
                }
              />
            ),
          },
          {
            key: 'log',
            label: t('job.tab.logs'),
            children: (
              <ProTable<JobLogVO, JobLogQuery>
                ref={logTableRef}
                rowKey="id"
                title={t('job.tab.logs')}
                columns={logColumns}
                request={pageJobLog}
                params={{ jobId: logJobId, status: logStatus }}
                onError={(error) => showError(error, t('job.log.msg.loadFailed'))}
                search={
                  <SearchForm
                    items={[
                      {
                        name: 'jobId',
                        label: t('job.list.search.jobId'),
                        element: (
                          <InputNumber
                            min={1}
                            placeholder={t('job.list.search.jobId')}
                            style={{ width: 140 }}
                          />
                        ),
                      },
                      {
                        name: 'status',
                        label: t('job.list.search.status'),
                        element: (
                          <Select
                            allowClear
                            options={buildLogStatusOptions(t)}
                            placeholder={t('job.list.search.all')}
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
            label: t('job.tab.alerts'),
            children: (
              <ProTable<JobAlertVO, JobAlertQuery>
                rowKey="id"
                title={t('job.tab.alerts')}
                columns={alertColumns}
                request={pageJobAlert}
                params={{ jobId: alertJobId, level: alertLevel, channel: alertChannel }}
                onError={(error) => showError(error, t('job.alert.msg.loadFailed'))}
                search={
                  <SearchForm
                    items={[
                      {
                        name: 'jobId',
                        label: t('job.list.search.jobId'),
                        element: (
                          <InputNumber
                            min={1}
                            placeholder={t('job.list.search.jobId')}
                            style={{ width: 140 }}
                          />
                        ),
                      },
                      {
                        name: 'level',
                        label: t('job.list.search.level'),
                        element: (
                          <Select
                            allowClear
                            options={buildAlertLevelOptions(t)}
                            placeholder={t('job.list.search.all')}
                            style={{ width: 160 }}
                          />
                        ),
                      },
                      {
                        name: 'channel',
                        label: t('job.list.search.channel'),
                        element: (
                          <Select
                            allowClear
                            options={buildAlertChannelOptions(t)}
                            placeholder={t('job.list.search.all')}
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
            label: t('job.tab.dag'),
            children: <JobDagPanel />,
          },
          ...(has('sys:job:log:list')
            ? [
                {
                  key: 'stats',
                  label: t('job.tab.stats'),
                  children: <JobStatsPanel />,
                },
              ]
            : []),
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
