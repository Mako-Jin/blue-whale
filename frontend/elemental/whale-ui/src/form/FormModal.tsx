// FormModal - 表单弹窗二次封装：打开时回填/重置、确认前校验、提交态托管

import { Form, Modal, type FormInstance } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';

export interface FormModalProps<V extends object> {
  open: boolean;
  title: ReactNode;
  /** 外部通过 Form.useForm() 创建并持有，便于读取/回填 */
  form: FormInstance<V>;
  /** 打开时回填的初始值（编辑场景传行数据） */
  initialValues?: Partial<V>;
  /** 校验通过后回调；返回 Promise 时自动托管确认按钮 loading */
  onOk: (values: V) => Promise<void> | void;
  onCancel: () => void;
  children: ReactNode;
  width?: number;
  okText?: string;
}

export function FormModal<V extends object>({
  open,
  title,
  form,
  initialValues,
  onOk,
  onCancel,
  children,
  width = 520,
  okText = '确定',
}: FormModalProps<V>) {
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.resetFields();
    if (initialValues) {
      // antd 的 setFieldsValue 期望 RecursivePartial，这里显式取形参类型做一次收敛
      form.setFieldsValue(initialValues as Parameters<FormInstance<V>['setFieldsValue']>[0]);
    }
  }, [open, form, initialValues]);

  const handleOk = async () => {
    let values: V;
    try {
      values = await form.validateFields();
    } catch {
      // 校验失败：antd 已在字段上高亮提示
      return;
    }
    setSubmitting(true);
    try {
      await onOk(values);
    } catch {
      // 业务错误由调用方负责提示；此处吞掉，避免未处理的 Promise 拒绝
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      width={width}
      okText={okText}
      confirmLoading={submitting}
      onOk={() => {
        void handleOk();
      }}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        {children}
      </Form>
    </Modal>
  );
}
