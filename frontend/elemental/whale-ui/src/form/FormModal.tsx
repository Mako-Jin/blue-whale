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

  // 打开时清理上一轮的字段状态（关闭时字段值因 preserve={false} 已随卸载清除，
  // 这里兜底处理非卸载路径）；初始值回填交给 <Form initialValues>：
  // Modal destroyOnClose 使 Form 每次打开都重新挂载，initialValues 随之重新生效，
  // 且不受「字段挂载时机早/晚于 setFieldsValue」的时序影响（Wave 5.73 修复：
  // 此前编辑回填依赖 effect 内 setFieldsValue，实际不生效，表现为文本字段空白）。
  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

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
      <Form form={form} layout="vertical" preserve={false} initialValues={initialValues}>
        {children}
      </Form>
    </Modal>
  );
}
