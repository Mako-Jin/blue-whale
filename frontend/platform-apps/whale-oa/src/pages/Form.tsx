// OA 请假表单：antd Modal + Form，新增与编辑两态，接 6 端点中的 create/update
import { useEffect } from 'react';
import { App, DatePicker, Form, Input, InputNumber, Modal, Select } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createLeave,
  updateLeave,
  type OaLeave,
  type OaLeaveInput,
  type OaLeavePeriod,
  type OaLeaveType,
} from '../api/oa';
import { getCurrentUser } from '../api/client';

/** 请假类型选项 */
const TYPE_OPTIONS: { value: OaLeaveType; label: string }[] = [
  { value: 'ANNUAL', label: '年假' },
  { value: 'SICK', label: '病假' },
  { value: 'PERSONAL', label: '事假' },
];

/** 时段选项 */
const PERIOD_OPTIONS: { value: OaLeavePeriod; label: string }[] = [
  { value: 'AM', label: 'AM 上午' },
  { value: 'PM', label: 'PM 下午' },
];

/** 表单字段：日期用 RangePicker 承载，提交时拆成 startDate / endDate */
interface FormValues {
  code: string;
  applicant: string;
  applicantId?: number;
  leaveType: OaLeaveType;
  range: [Dayjs, Dayjs];
  startPeriod: OaLeavePeriod;
  endPeriod: OaLeavePeriod;
  handoverUserId?: number;
  days: number;
  reason?: string;
  remark?: string;
}

interface FormModalProps {
  /** 弹窗可见性 */
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: OaLeave | null;
  /** 取消回调 */
  onCancel: () => void;
  /** 保存成功回调（父组件关闭弹窗并刷新列表） */
  onSaved: () => void;
}

export default function FormModal({ open, initial, onCancel, onSaved }: FormModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();

  // 弹窗打开时按编辑/新增初始化表单值
  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        code: initial.code,
        applicant: initial.applicant,
        applicantId: initial.applicantId,
        leaveType: initial.leaveType,
        range: [dayjs(initial.startDate), dayjs(initial.endDate)],
        startPeriod: initial.startPeriod ?? 'AM',
        endPeriod: initial.endPeriod ?? 'PM',
        handoverUserId: initial.handoverUserId,
        days: initial.days,
        reason: initial.reason,
        remark: initial.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        leaveType: 'ANNUAL',
        applicant: getCurrentUser()?.nickname ?? getCurrentUser()?.username ?? '',
        startPeriod: 'AM',
        endPeriod: 'PM',
      });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: OaLeaveInput = {
      code: values.code.trim(),
      applicant: values.applicant.trim(),
      applicantId: values.applicantId ?? undefined,
      leaveType: values.leaveType,
      startDate: values.range[0].format('YYYY-MM-DD'),
      startPeriod: values.startPeriod,
      endDate: values.range[1].format('YYYY-MM-DD'),
      endPeriod: values.endPeriod,
      handoverUserId: values.handoverUserId ?? undefined,
      days: values.days,
      reason: values.reason?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
    };
    try {
      if (initial) {
        await updateLeave(initial.id, payload);
      } else {
        await createLeave(payload);
      }
      message.success('保存成功');
      form.resetFields();
      onSaved();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑请假申请' : '新增请假申请'}
      onOk={() => void handleOk()}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="code"
          label="申请单号"
          rules={[{ required: true, message: '请输入申请单号' }]}
        >
          <Input placeholder="如 OA-LV-202610-001" maxLength={64} />
        </Form.Item>
        <Form.Item
          name="applicant"
          label="申请人"
          rules={[{ required: true, message: '请输入申请人' }]}
        >
          <Input placeholder="请输入申请人" maxLength={64} />
        </Form.Item>
        <Form.Item
          name="applicantId"
          label="申请人用户ID"
          extra="填用户ID后校验档案并冻结额度"
        >
          <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
        </Form.Item>
        <Form.Item
          name="leaveType"
          label="请假类型"
          rules={[{ required: true, message: '请选择请假类型' }]}
        >
          <Select options={TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="range"
          label="请假区间"
          rules={[{ required: true, message: '请选择请假区间' }]}
        >
          <DatePicker.RangePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="startPeriod"
          label="开始时段"
          rules={[{ required: true, message: '请选择开始时段' }]}
        >
          <Select options={PERIOD_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="endPeriod"
          label="结束时段"
          rules={[{ required: true, message: '请选择结束时段' }]}
        >
          <Select options={PERIOD_OPTIONS} />
        </Form.Item>
        <Form.Item name="handoverUserId" label="工作交接人用户ID">
          <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
        </Form.Item>
        <Form.Item
          name="days"
          label="请假天数"
          rules={[
            { required: true, message: '请输入请假天数' },
            { type: 'number', min: 0.5, max: 365, message: '天数需在 0.5 ~ 365 之间' },
          ]}
        >
          <InputNumber style={{ width: '100%' }} min={0.5} max={365} step={0.5} precision={1} />
        </Form.Item>
        <Form.Item name="reason" label="事由">
          <Input.TextArea rows={3} maxLength={512} showCount placeholder="选填" />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
