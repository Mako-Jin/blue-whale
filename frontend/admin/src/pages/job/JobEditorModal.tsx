// 定时任务新增/编辑弹窗视图：任务字段表单 + 编辑场景的上游依赖选择与下游依赖提示

import { Form, Input, InputNumber, Select, Typography, type FormInstance } from 'antd';
import { FormModal } from '@bluewhale/whale-ui';
import type { JobDependencyVO, JobInfoVO } from '../../api/job';
import { JOB_STATUS_OPTIONS, MISFIRE_OPTIONS, type JobFormValues } from './constants';

interface JobEditorModalProps {
  open: boolean;
  editRecord: JobInfoVO | null;
  initial?: Partial<JobFormValues>;
  form: FormInstance<JobFormValues>;
  dependency: JobDependencyVO | null;
  dependencyLoading: boolean;
  jobOptions: { value: number; label: string }[];
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
  return (
    <FormModal<JobFormValues>
      open={open}
      title={editRecord ? `编辑任务：${editRecord.jobName}` : '新增任务'}
      form={form}
      initialValues={initial}
      onOk={onOk}
      onCancel={onCancel}
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
  );
}
