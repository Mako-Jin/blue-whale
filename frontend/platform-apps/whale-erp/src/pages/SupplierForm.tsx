// 供应商表单：新增/编辑两态，提交到 ERP 供应商主数据接口
import { useEffect } from 'react';
import { Form, Input, Modal, Select, message } from 'antd';
import {
  saveErpSupplier,
  type ErpSupplier,
  type ErpSupplierInput,
} from '../api/erp';

interface SupplierFormModalProps {
  /** 弹窗可见性 */
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: ErpSupplier | null;
  /** 取消回调 */
  onCancel: () => void;
  /** 保存成功回调（父组件关闭弹窗并刷新列表） */
  onSaved: () => void;
}

/** 分类选项 */
const CATEGORY_OPTIONS = [
  { value: 'MATERIAL', label: '物料' },
  { value: 'SERVICE', label: '服务' },
  { value: 'OUTSOURCE', label: '外协' },
  { value: 'LOGISTICS', label: '物流' },
  { value: 'OTHER', label: '其他' },
];

export default function SupplierFormModal({
  open,
  initial,
  onCancel,
  onSaved,
}: SupplierFormModalProps) {
  const [form] = Form.useForm<ErpSupplierInput>();

  // 弹窗打开时按编辑/新增初始化表单值
  useEffect(() => {
    if (!open) return;
    if (initial) {
      form.setFieldsValue(initial);
    } else {
      form.resetFields();
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    await saveErpSupplier({ ...values, id: initial?.id });
    message.success('保存成功');
    form.resetFields();
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑供应商' : '新增供应商'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
      width={680}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ category: 'MATERIAL', status: 'ENABLED' }}
      >
        <Form.Item name="code" label="供应商编码" rules={[{ required: true, message: '请输入供应商编码' }]}>
          <Input placeholder="如 SUP-001" maxLength={64} />
        </Form.Item>
        <Form.Item name="name" label="供应商名称" rules={[{ required: true, message: '请输入供应商名称' }]}>
          <Input placeholder="请输入供应商名称" maxLength={128} />
        </Form.Item>
        <Form.Item name="shortName" label="供应商简称">
          <Input placeholder="选填" maxLength={64} />
        </Form.Item>
        <Form.Item name="category" label="供应商分类" rules={[{ required: true, message: '请选择分类' }]}>
          <Select options={CATEGORY_OPTIONS} />
        </Form.Item>
        <Form.Item name="contactName" label="联系人">
          <Input placeholder="选填" maxLength={64} />
        </Form.Item>
        <Form.Item name="contactPhone" label="联系电话">
          <Input placeholder="选填" maxLength={32} />
        </Form.Item>
        <Form.Item
          name="contactEmail"
          label="联系邮箱"
          rules={[{ type: 'email', message: '邮箱格式不正确' }]}
        >
          <Input placeholder="选填" maxLength={128} />
        </Form.Item>
        <Form.Item name="taxNo" label="纳税人识别号">
          <Input placeholder="选填" maxLength={64} />
        </Form.Item>
        <Form.Item name="bankName" label="开户银行">
          <Input placeholder="选填" maxLength={128} />
        </Form.Item>
        <Form.Item name="bankAccount" label="银行账号">
          <Input placeholder="选填" maxLength={64} />
        </Form.Item>
        <Form.Item name="address" label="地址">
          <Input placeholder="选填" maxLength={255} />
        </Form.Item>
        <Form.Item name="rating" label="供应商评级">
          <Select
            allowClear
            placeholder="选填"
            options={[
              { value: 'A', label: 'A' },
              { value: 'B', label: 'B' },
              { value: 'C', label: 'C' },
              { value: 'D', label: 'D' },
            ]}
          />
        </Form.Item>
        <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
          <Select
            options={[
              { value: 'ENABLED', label: '启用' },
              { value: 'DISABLED', label: '禁用' },
            ]}
          />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} placeholder="选填" maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
