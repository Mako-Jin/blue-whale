// MES 生产工单表单页：antd Modal + Form，覆盖 M0 工单生命周期与计划字段
import { useEffect } from 'react';
import { DatePicker, Form, Input, InputNumber, Modal, Select, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  saveMesWorkOrder,
  type MesWorkOrder,
  type MesWorkOrderInput,
  type MesWorkOrderPriority,
  type MesWorkOrderStatus,
} from '../api/mes';

interface FormModalProps {
  /** 弹窗可见性 */
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: MesWorkOrder | null;
  /** 取消回调 */
  onCancel: () => void;
  /** 保存成功回调（父组件关闭弹窗并刷新列表） */
  onSaved: () => void;
}

/** 表单值：计划时间用 Dayjs 承载，提交时转为 ISO 字符串 */
type FormValues = Omit<MesWorkOrderInput, 'id' | 'plannedStart' | 'plannedEnd'> & {
  plannedStart?: Dayjs;
  plannedEnd?: Dayjs;
};

/** 工单生命周期选项 */
const STATUS_OPTIONS: Array<{ value: MesWorkOrderStatus; label: string }> = [
  { value: 'RECEIVED', label: '已接收' },
  { value: 'DISPATCHED', label: '已派工' },
  { value: 'IN_PROGRESS', label: '生产中' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'COMPLETED', label: '已完工' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'CANCELLED', label: '已取消' },
];

/** 优先级选项 */
const PRIORITY_OPTIONS: Array<{ value: MesWorkOrderPriority; label: string }> = [
  { value: 'NORMAL', label: '普通' },
  { value: 'URGENT', label: '紧急' },
];

/** ISO 时间字符串转 Dayjs，空值返回 undefined */
function toDayjs(value?: string): Dayjs | undefined {
  return value ? dayjs(value) : undefined;
}

export default function FormModal({ open, initial, onCancel, onSaved }: FormModalProps) {
  const [form] = Form.useForm<FormValues>();

  // 弹窗打开时按编辑/新增初始化表单值
  useEffect(() => {
    if (!open) return;
    if (initial) {
      form.setFieldsValue({
        code: initial.code,
        name: initial.name,
        status: initial.status,
        erpOrderNo: initial.erpOrderNo,
        itemCode: initial.itemCode,
        itemName: initial.itemName,
        plannedQuantity:
          initial.plannedQuantity === undefined || initial.plannedQuantity === null
            ? undefined
            : Number(initial.plannedQuantity),
        uom: initial.uom,
        routingVersion: initial.routingVersion,
        priority: initial.priority,
        plannedStart: toDayjs(initial.plannedStart),
        plannedEnd: toDayjs(initial.plannedEnd),
        remark: initial.remark,
      });
    } else {
      form.resetFields();
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    await saveMesWorkOrder({
      ...values,
      id: initial?.id,
      plannedStart: values.plannedStart?.format('YYYY-MM-DDTHH:mm:ss'),
      plannedEnd: values.plannedEnd?.format('YYYY-MM-DDTHH:mm:ss'),
    });
    message.success('保存成功');
    form.resetFields();
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑生产工单' : '新增生产工单'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
      width={720}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ status: 'RECEIVED', priority: 'NORMAL' }}
      >
        <Form.Item
          name="code"
          label="工单编码"
          rules={[{ required: true, message: '请输入工单编码' }]}
        >
          <Input placeholder="如 MES-001" maxLength={64} />
        </Form.Item>
        <Form.Item
          name="name"
          label="工单名称"
          rules={[{ required: true, message: '请输入工单名称' }]}
        >
          <Input placeholder="请输入工单名称" maxLength={128} />
        </Form.Item>
        <Form.Item name="erpOrderNo" label="ERP 生产订单号">
          <Input placeholder="来自 ERP 下达的生产订单号，可为空" maxLength={64} />
        </Form.Item>
        <Form.Item name="itemCode" label="物料编码">
          <Input placeholder="如 DEMO-ITEM" maxLength={64} />
        </Form.Item>
        <Form.Item name="itemName" label="物料名称">
          <Input placeholder="选填" maxLength={128} />
        </Form.Item>
        <Form.Item name="plannedQuantity" label="计划数量">
          <InputNumber min={0} precision={4} style={{ width: '100%' }} placeholder="如 100" />
        </Form.Item>
        <Form.Item name="uom" label="计量单位">
          <Input placeholder="如 PCS / KG" maxLength={16} />
        </Form.Item>
        <Form.Item name="routingVersion" label="工艺路线版本">
          <Input placeholder="如 V1，来自工艺路线快照" maxLength={32} />
        </Form.Item>
        <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
          <Select options={PRIORITY_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="status"
          label="状态"
          rules={[{ required: true, message: '请选择状态' }]}
          extra="状态按生命周期流转校验，后端会拒绝非法迁移"
        >
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="plannedStart" label="计划开工时间">
          <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" />
        </Form.Item>
        <Form.Item name="plannedEnd" label="计划完工时间">
          <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm:ss" />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} maxLength={255} placeholder="选填" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
