// 定时任务新增/编辑弹窗视图：任务字段表单 + 编辑场景的上游依赖选择与下游依赖提示

import { Form, Input, InputNumber, Select, Typography, type FormInstance } from 'antd';
import { FormModal } from '@bluewhale/whale-ui';
import type { JobDependencyVO, JobInfoVO } from '../../api/job';
import { useI18n } from '../../i18n';
import { buildJobStatusOptions, buildMisfireOptions, type JobFormValues } from './constants';

interface JobEditorModalProps {
  open: boolean;
  editRecord: JobInfoVO | null;
  initial?: Partial<JobFormValues>;
  form: FormInstance<JobFormValues>;
  dependency: JobDependencyVO | null;
  dependencyLoading: boolean;
  jobOptions: { value: string; label: string }[];
  onOk: (values: JobFormValues) => void | Promise<void>;
  onCancel: () => void;
}

export function JobEditorModal({
  open,
  editRecord,
  initial,
  form,
  dependency,
  dependencyLoading,
  jobOptions,
  onOk,
  onCancel,
}: JobEditorModalProps) {
  const { t } = useI18n();
  return (
    <FormModal<JobFormValues>
      open={open}
      title={
        editRecord
          ? t('job.editor.titleEdit', { name: editRecord.jobName })
          : t('job.editor.titleCreate')
      }
      form={form}
      initialValues={initial}
      onOk={onOk}
      onCancel={onCancel}
    >
      <Form.Item
        name="jobName"
        label={t('job.editor.field.jobName')}
        rules={[{ required: true, message: t('job.editor.rule.jobName') }]}
      >
        <Input />
      </Form.Item>
      <Form.Item name="jobGroup" label={t('job.editor.field.jobGroup')}>
        <Input placeholder={t('job.editor.placeholder.jobGroup')} />
      </Form.Item>
      <Form.Item
        name="cronExpression"
        label={t('job.editor.field.cron')}
        rules={[{ required: true, message: t('job.editor.rule.cron') }]}
      >
        <Input placeholder={t('job.editor.placeholder.cron')} />
      </Form.Item>
      <Form.Item
        name="handlerBean"
        label={t('job.editor.field.handler')}
        rules={[{ required: true, message: t('job.editor.rule.handler') }]}
      >
        <Input placeholder={t('job.editor.placeholder.handler')} />
      </Form.Item>
      <Form.Item name="handlerParam" label={t('job.editor.field.handlerParam')}>
        <Input />
      </Form.Item>
      <Form.Item name="misfirePolicy" label={t('job.editor.field.misfire')} initialValue="SMART">
        <Select options={buildMisfireOptions(t)} />
      </Form.Item>
      <Form.Item
        name="maxRetry"
        label={t('job.editor.field.maxRetry')}
        initialValue={0}
        extra={t('job.editor.extra.maxRetry')}
      >
        <InputNumber min={0} max={5} precision={0} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item
        name="retryIntervalSeconds"
        label={t('job.editor.field.retryInterval')}
        initialValue={60}
        extra={t('job.editor.extra.retryInterval')}
      >
        <InputNumber min={0} precision={0} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item
        name="shardTotal"
        label={t('job.editor.field.shardTotal')}
        initialValue={1}
        extra={t('job.editor.extra.shardTotal')}
      >
        <InputNumber min={1} max={16} precision={0} style={{ width: '100%' }} />
      </Form.Item>
      {editRecord && (
        <Form.Item name="upstreamJobIds" label={t('job.editor.field.upstream')}>
          <Select
            mode="multiple"
            allowClear
            showSearch
            optionFilterProp="label"
            loading={dependencyLoading}
            options={jobOptions}
            placeholder={t('job.editor.placeholder.upstream')}
          />
        </Form.Item>
      )}
      {editRecord && (dependency?.downstreamJobIds.length ?? 0) > 0 && (
        <Form.Item label={t('job.editor.field.downstream')}>
          <Typography.Text type="secondary">
            {t('job.editor.downstreamCount', { count: dependency?.downstreamJobIds.length ?? 0 })}
          </Typography.Text>
        </Form.Item>
      )}
      <Form.Item name="status" label={t('job.editor.field.status')} initialValue={0}>
        <Select options={buildJobStatusOptions(t)} />
      </Form.Item>
      <Form.Item name="remark" label={t('job.editor.field.remark')}>
        <Input.TextArea rows={2} />
      </Form.Item>
    </FormModal>
  );
}
