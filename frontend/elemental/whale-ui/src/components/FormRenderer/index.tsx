// FormRenderer - 受控动态表单渲染器（表单设计器「预览」与工作流填报/审批页共用）
// 数据源为 GET /form/definition/{id} 的 fields[]（天然含纯布局字段），提交键 = columnCode。
// 纯布局控件 title / divider / text-note 只渲染、不绑定表单值。

import { useEffect } from 'react';
import {
  Checkbox,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Radio,
  Row,
  Select,
  Switch,
  Typography,
} from 'antd';
import dayjs from 'dayjs';

/** 后端 Long 类主键/外键，统一以字符串承载（雪花 ID 在 JSON 中一律为字符串） */
export type WhaleFormId = string;

/** 列存储类型（与后端 DTO/VO 字段名保持一致） */
export type WhaleFormDataType =
  'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool';

/** 控件类型；后 3 个（title/divider/text-note）为纯布局控件 */
export type WhaleFormWidgetType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'select'
  | 'radio'
  | 'checkbox'
  | 'date'
  | 'datetime'
  | 'switch'
  | 'title'
  | 'divider'
  | 'text-note';

/** 字段选项（对应后端 FormFieldOptionDTO / FormFieldOptionVO） */
export interface WhaleFormFieldOption {
  optionLabel: string;
  optionValue: string;
  sortNo?: number;
}

/** 表单字段配置（对应后端 FormFieldConfigDTO / FormFieldConfigVO，字段名严格一致） */
export interface WhaleFormFieldConfig {
  id?: WhaleFormId;
  columnMetaId?: WhaleFormId | null;
  fieldLabel: string;
  widgetType?: WhaleFormWidgetType;
  showInList?: number;
  showInForm?: number;
  editable?: number;
  sortNo?: number;
  colSpan?: number;
  placeholder?: string;
  defaultValue?: string;
  validateJson?: string;
  remark?: string;
  status?: number;
  options?: WhaleFormFieldOption[];
  // 关联列信息冗余回填（来自后端 enrich；columnMetaId 为 null 的纯布局字段为 undefined）
  columnCode?: string;
  dataType?: WhaleFormDataType;
  required?: number;
  maxLength?: number;
}

/** 渲染器入参（设计器预览与填报页共用） */
export interface FormRendererProps {
  /** 字段配置，数组顺序即渲染顺序 */
  fields: WhaleFormFieldConfig[];
  /** 受控值，键为 columnCode */
  value?: Record<string, unknown>;
  /** 值变化回调：回传全量字段值（switch 已由 boolean 转回 1/0） */
  onChange?: (values: Record<string, unknown>) => void;
  /** 整体禁用（只读预览等） */
  disabled?: boolean;
}

/** 纯布局控件集合 */
const LAYOUT_WIDGETS: readonly WhaleFormWidgetType[] = ['title', 'divider', 'text-note'];

/** 是否纯布局字段：无关联列、不绑定表单值、不参与提交 */
function isLayoutWidget(widgetType?: WhaleFormWidgetType): boolean {
  return widgetType !== undefined && LAYOUT_WIDGETS.includes(widgetType);
}

/** FieldOption → antd options */
function toOptions(field: WhaleFormFieldConfig): { label: string; value: string }[] {
  return (field.options ?? []).map((option) => ({
    label: option.optionLabel,
    value: option.optionValue,
  }));
}

/** 外部值 → 内部值：switch 的 1/0 转 boolean；日期字符串转 dayjs；多选字符串转数组 */
function toInternalValues(
  fields: WhaleFormFieldConfig[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const internal: Record<string, unknown> = {};
  fields.forEach((field) => {
    const code = field.columnCode;
    if (!code || isLayoutWidget(field.widgetType)) {
      return;
    }
    const current = values[code];
    if (current === undefined || current === null) {
      // 显式置为 undefined，保证外部清空（重置）时内部值同步清空
      internal[code] = undefined;
    } else if (field.widgetType === 'switch') {
      internal[code] = current === 1 || current === '1' || current === true;
    } else if (
      (field.widgetType === 'date' || field.widgetType === 'datetime') &&
      typeof current === 'string'
    ) {
      internal[code] = dayjs(current);
    } else if (field.widgetType === 'checkbox' && typeof current === 'string') {
      internal[code] = current ? current.split(',') : [];
    } else {
      internal[code] = current;
    }
  });
  return internal;
}

/** 内部值 → 外部值：switch 的 boolean 转回 1/0 */
function toExternalValues(
  fields: WhaleFormFieldConfig[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const external: Record<string, unknown> = { ...values };
  fields.forEach((field) => {
    const code = field.columnCode;
    if (!code || field.widgetType !== 'switch') {
      return;
    }
    const current = external[code];
    if (current !== undefined && current !== null) {
      external[code] = current ? 1 : 0;
    }
  });
  return external;
}

/** 渲染纯布局字段（文本统一取 fieldLabel） */
function renderLayout(field: WhaleFormFieldConfig) {
  if (field.widgetType === 'divider') {
    return <Divider />;
  }
  if (field.widgetType === 'text-note') {
    return <Typography.Text type="secondary">{field.fieldLabel}</Typography.Text>;
  }
  return <Typography.Title level={5}>{field.fieldLabel}</Typography.Title>;
}

/** 渲染数据字段控件（disabled 已由调用方合并「整体禁用 + editable=0」） */
function renderControl(field: WhaleFormFieldConfig, disabled: boolean) {
  switch (field.widgetType) {
    case 'textarea':
      return <Input.TextArea placeholder={field.placeholder} disabled={disabled} />;
    case 'number':
      return <InputNumber placeholder={field.placeholder} disabled={disabled} />;
    case 'select':
      return (
        <Select
          placeholder={field.placeholder}
          allowClear
          disabled={disabled}
          options={toOptions(field)}
        />
      );
    case 'radio':
      return <Radio.Group options={toOptions(field)} disabled={disabled} />;
    case 'checkbox':
      return <Checkbox.Group options={toOptions(field)} disabled={disabled} />;
    case 'date':
      return <DatePicker format="YYYY-MM-DD" placeholder={field.placeholder} disabled={disabled} />;
    case 'datetime':
      return (
        <DatePicker
          showTime
          format="YYYY-MM-DD HH:mm:ss"
          placeholder={field.placeholder}
          disabled={disabled}
        />
      );
    case 'switch':
      return <Switch disabled={disabled} />;
    default:
      // text（含 widgetType 缺省）
      return (
        <Input
          maxLength={field.maxLength ?? 255}
          placeholder={field.placeholder}
          disabled={disabled}
        />
      );
  }
}

/** 受控动态表单渲染器：字段配置驱动渲染，值双向同步（switch 1/0 ↔ boolean、日期 ↔ dayjs、多选 ↔ 逗号串） */
export function FormRenderer({ fields, value, onChange, disabled }: FormRendererProps) {
  const [form] = Form.useForm();

  // 受控同步：外部 value 变化时写入内部 Form（含 switch 1/0 ↔ boolean 等类型转换）
  useEffect(() => {
    if (value === undefined) {
      return;
    }
    form.setFieldsValue(toInternalValues(fields, value));
  }, [fields, form, value]);

  // 内部值变化 → 回传全量字段值（switch 转回 1/0）
  const handleValuesChange = (
    _changedValues: Record<string, unknown>,
    allValues: Record<string, unknown>,
  ) => {
    onChange?.(toExternalValues(fields, allValues));
  };

  // 渲染单个字段：布局字段 / 数据字段（无 columnCode 的脏数据不渲染）
  const renderField = (field: WhaleFormFieldConfig) => {
    if (isLayoutWidget(field.widgetType)) {
      return renderLayout(field);
    }
    const columnCode = field.columnCode;
    if (!columnCode) {
      return null;
    }
    return (
      <Form.Item
        name={columnCode}
        label={field.fieldLabel}
        rules={
          field.required === 1
            ? [{ required: true, message: `请输入${field.fieldLabel}` }]
            : undefined
        }
        valuePropName={field.widgetType === 'switch' ? 'checked' : 'value'}
      >
        {renderControl(field, !!disabled || field.editable === 0)}
      </Form.Item>
    );
  };

  // 仅渲染表单中可见字段（showInForm !== 0，undefined 视为显示）
  const visibleFields = fields.filter((field) => field.showInForm !== 0);

  return (
    <Form form={form} layout="vertical" onValuesChange={handleValuesChange}>
      <Row gutter={16}>
        {visibleFields.map((field, index) => (
          <Col
            key={field.id ?? `${field.columnCode ?? 'layout'}-${index}`}
            span={field.colSpan ?? 24}
          >
            {renderField(field)}
          </Col>
        ))}
      </Row>
    </Form>
  );
}
