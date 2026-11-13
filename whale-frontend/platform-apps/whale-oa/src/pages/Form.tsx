// OA 请假表单：antd Modal + Form，新增与编辑两态，接 6 端点中的 create/update
import { useEffect, useState } from 'react';
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Select, Upload } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
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
import { uploadOaFile } from '../api/file';

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

/** 单笔申请附件上限 */
const MAX_ATTACHMENTS = 5;

/** 单个附件大小上限：20MB */
const MAX_ATTACHMENT_SIZE = 20 * 1024 * 1024;

/** 后端登记了附件要求的假期类型（SICK 由 M0 种子数据置为 requires_attachment=1） */
const REQUIRES_ATTACHMENT_TYPES: OaLeaveType[] = ['SICK'];

/** 新上传文件响应 */
interface UploadedResponse {
  id: number;
  fileName?: string;
}

/** 既有附件响应 */
interface ExistingResponse {
  refId: number;
}

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
  const [fileList, setFileList] = useState<UploadFile[]>([]);

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
      setFileList(
        (initial.attachments ?? []).map((attachment) => ({
          uid: `ref-${attachment.id}`,
          name: attachment.fileName,
          status: 'done',
          response: { refId: attachment.id } satisfies ExistingResponse,
        })),
      );
    } else {
      form.resetFields();
      form.setFieldsValue({
        leaveType: 'ANNUAL',
        applicant: getCurrentUser()?.nickname ?? getCurrentUser()?.username ?? '',
        startPeriod: 'AM',
        endPeriod: 'PM',
      });
      setFileList([]);
    }
  }, [open, initial, form]);

  /** 附件选择前置校验：超过 20MB 的文件不进入待上传列表 */
  const handleBeforeUpload = (file: File) => {
    if (file.size > MAX_ATTACHMENT_SIZE) {
      message.error('单个附件不能超过 20MB');
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  /** 自定义上传：成功后把文件ID写入响应，失败时移出列表 */
  const handleUpload: UploadProps['customRequest'] = (options) => {
    const file = options.file as unknown as File & { uid: string };
    void (async () => {
      try {
        const uploaded = await uploadOaFile(file);
        setFileList((prev) =>
          prev.map((item) =>
            item.uid === file.uid ? { ...item, status: 'done', response: uploaded } : item,
          ),
        );
        options.onSuccess?.(uploaded);
      } catch (error) {
        setFileList((prev) => prev.filter((item) => item.uid !== file.uid));
        options.onError?.(error instanceof Error ? error : new Error('上传失败'));
        message.error(error instanceof Error ? error.message : '附件上传失败');
      }
    })();
  };

  /** 受控列表变更：保留自定义上传写入的响应，避免 antd 回调覆盖 */
  const handleFileListChange: UploadProps['onChange'] = ({ fileList: next }) => {
    setFileList((prev) =>
      next.map((item) => {
        const previous = prev.find((candidate) => candidate.uid === item.uid);
        return previous?.response ? { ...item, response: previous.response } : item;
      }),
    );
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const doneFiles = fileList.filter((item) => item.status === 'done');
    if (fileList.some((item) => item.status === 'uploading')) {
      message.warning('附件上传中，请稍候');
      return;
    }
    if (REQUIRES_ATTACHMENT_TYPES.includes(values.leaveType) && doneFiles.length === 0) {
      message.warning('病假必须上传附件（1–5 个）');
      return;
    }
    const fileIds = doneFiles
      .map((item) => (item.response as UploadedResponse | undefined)?.id)
      .filter((id): id is number => typeof id === 'number');
    const keepAttachmentIds = doneFiles
      .map((item) => (item.response as ExistingResponse | undefined)?.refId)
      .filter((id): id is number => typeof id === 'number');
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
      fileIds,
      keepAttachmentIds: initial ? keepAttachmentIds : undefined,
    };
    try {
      if (initial) {
        await updateLeave(initial.id, payload);
      } else {
        await createLeave(payload);
      }
      message.success('保存成功');
      form.resetFields();
      setFileList([]);
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
        <Form.Item name="applicantId" label="申请人用户ID" extra="填用户ID后校验档案并冻结额度">
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
        <Form.Item label="附件" extra="病假必须上传；最多 5 个，单个不超过 20MB">
          <Upload
            multiple
            maxCount={MAX_ATTACHMENTS}
            fileList={fileList}
            beforeUpload={handleBeforeUpload}
            customRequest={handleUpload}
            onChange={handleFileListChange}
            onRemove={(file) => {
              setFileList((prev) => prev.filter((item) => item.uid !== file.uid));
              return true;
            }}
          >
            <Button>选择文件</Button>
          </Upload>
        </Form.Item>
      </Form>
    </Modal>
  );
}
