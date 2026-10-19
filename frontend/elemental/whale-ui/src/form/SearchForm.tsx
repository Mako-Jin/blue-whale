// SearchForm - 查询区二次封装：字段配置 + 查询/重置按钮 + 表单实例回收

import { Button, Form, Space, type FormInstance } from 'antd';
import { useEffect, type ReactNode } from 'react';

export interface SearchFormItem {
  /** 表单字段名（Form.Item name），不传则视为纯展示项 */
  name?: string;
  label: ReactNode;
  /** 具体控件（Input / Select / DatePicker 等） */
  element: ReactNode;
}

export interface SearchFormProps {
  items: SearchFormItem[];
  /** 点击「查询」回调，入参为表单当前值 */
  onSearch: (values: Record<string, unknown>) => void;
  /** 点击「重置」回调（表单已清空后再触发） */
  onReset?: () => void;
  loading?: boolean;
  initialValues?: Record<string, unknown>;
  /** 暴露表单实例，便于外部回填 */
  form?: FormInstance;
  extra?: ReactNode;
}

export function SearchForm({
  items,
  onSearch,
  onReset,
  loading,
  initialValues,
  form,
  extra,
}: SearchFormProps) {
  const [innerForm] = Form.useForm();
  const currentForm = form ?? innerForm;

  // 避免 initialValues 变化后表单未同步
  useEffect(() => {
    currentForm.resetFields();
  }, [currentForm, initialValues]);

  const handleReset = () => {
    currentForm.resetFields();
    onReset?.();
  };

  return (
    <Form
      form={currentForm}
      layout="inline"
      initialValues={initialValues}
      onFinish={(values) => onSearch(values as Record<string, unknown>)}
      style={{ rowGap: 12, marginBottom: 16 }}
    >
      {items.map((item, index) => (
        <Form.Item key={item.name ?? index} name={item.name} label={item.label}>
          {item.element}
        </Form.Item>
      ))}
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={loading}>
            查询
          </Button>
          <Button onClick={handleReset}>重置</Button>
          {extra}
        </Space>
      </Form.Item>
    </Form>
  );
}
