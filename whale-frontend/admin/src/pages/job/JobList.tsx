// 定时任务管理页 - 容器：任务列表 / 执行日志 / 告警记录三个页签的状态与操作处理。
// 列定义见 columns.tsx，弹窗视图见 JobEditorModal.tsx，常量与类型见 constants.ts。
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import { App, Form, Input, InputNumber, Select, Tabs } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
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
import { alertColumns, buildJobColumns, logColumns } from './columns';
import {
  JOB_STATUS_OPTIONS,
  LOG_STATUS_OPTIONS,
  type JobAlertQuery,
  type JobFormValues,
  type JobLogQuery,
  type JobQuery,
} from './constants';
import { JobEditorModal } from './JobEditorModal';

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
                        rawJobId === undefined || rawJobId === null ? undefined : String(rawJobId),
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
    </>
  );
}
