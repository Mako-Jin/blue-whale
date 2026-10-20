// OA 表单页：antd Modal + Form，新增与编辑两态，占位提交
import { useEffect } from 'react';
import { Form, Input, Modal, Select, message } from 'antd';
import { saveOaDocument, type OaDocument, type OaDocumentInput } from '../api/oa';

interface FormModalProps {
  /** 弹窗可见性 */
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: OaDocument | null;
  /** 取消回调 */
  onCancel: () => void;
  /** 保存成功回调（父组件关闭弹窗并刷新列表） */
  onSaved: () => void;
}

export default function FormModal({ open, initial, onCancel, onSaved }: FormModalProps) {
  const [form] = Form.useForm<OaDocumentInput>();

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
    await saveOaDocument({ ...values, id: initial?.id });
    message.success('保存成功');
    form.resetFields();
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑' : '新增'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ status: 'ENABLED' }}>
        <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
          <Input placeholder="如 OA-001" />
        </Form.Item>
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="请输入名称" />
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
          <Input.TextArea rows={3} placeholder="选填" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
