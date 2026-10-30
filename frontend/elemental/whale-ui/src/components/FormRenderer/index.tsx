// FormRenderer - 受控动态表单渲染器（表单设计器「预览」与工作流填报/审批页共用）
// 数据源为 GET /form/definition/{id} 的 fields[]（天然含纯布局字段），提交键 = columnCode。
// 纯布局控件 title / divider / text-note 只渲染、不绑定表单值。
// 复合字段：attachment（文件 ID 数组）与 subtable（行对象数组），扩展配置可来自 validateJson。
// 本文件为容器/聚合入口：类型见 types.ts、值转换见 values.ts、文件运行时见 fileRuntime.ts、
// 控件渲染分发见 controls.tsx（附件/子表控件各自独立文件）。

import { useEffect, useState } from 'react';
import { Col, Form, Row } from 'antd';
import { renderControl, renderLayout } from './controls';
import type { FileRuntime } from './fileRuntime';
import { isFieldDisabled, isFieldRequired, isFieldVisible } from './linkage';
import { isLayoutWidget, resolveFieldExtras, toExternalValues, toInternalValues } from './values';
import type { FormRendererProps, WhaleFormFieldConfig } from './types';

export type {
  WhaleFormId,
  WhaleFormBasicDataType,
  WhaleFormDataType,
  WhaleFormWidgetType,
  WhaleFormFieldOption,
  WhaleFormSubField,
  WhaleUploadedFile,
  WhaleFormFieldConfig,
  FormRendererProps,
} from './types';

/** 受控动态表单渲染器：字段配置驱动渲染，值双向同步（switch 1/0 ↔ boolean、日期 ↔ dayjs、多选 ↔ 逗号串） */
export function FormRenderer({
  fields,
  value,
  onChange,
  disabled,
  authToken,
  apiBaseUrl,
  uploadFile,
  downloadFile,
}: FormRendererProps) {
  const [form] = Form.useForm();
  const runtime: FileRuntime = { authToken, apiBaseUrl, uploadFile, downloadFile };
  // 联动评估所用的当前表单值（与外部受控 value 同形，内部编辑时实时更新）
  const [linkageValues, setLinkageValues] = useState<Record<string, unknown>>(value ?? {});

  // 受控同步：外部 value 变化时写入内部 Form（含 switch 1/0 ↔ boolean 等类型转换）
  useEffect(() => {
    if (value === undefined) {
      return;
    }
    form.setFieldsValue(toInternalValues(fields, value));
    setLinkageValues(value);
  }, [fields, form, value]);

  // 内部值变化 → 回传全量字段值（switch 转回 1/0）
  const handleValuesChange = (
    _changedValues: Record<string, unknown>,
    allValues: Record<string, unknown>,
  ) => {
    const external = toExternalValues(fields, allValues);
    setLinkageValues(external);
    onChange?.(external);
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
    const resolved = resolveFieldExtras(field);
    const multiValue = field.widgetType === 'attachment' || field.widgetType === 'subtable';
    // 联动：无 linkage 时 required/disabled 判定与改动前一致
    const linkageField = {
      key: columnCode,
      required: field.required === 1,
      linkage: field.linkage,
    };
    const required = isFieldRequired(linkageField, linkageValues);
    const fieldDisabled =
      !!disabled || field.editable === 0 || isFieldDisabled(linkageField, linkageValues);
    return (
      <Form.Item
        name={columnCode}
        label={field.fieldLabel}
        rules={
          required
            ? [
                multiValue
                  ? {
                      required: true,
                      type: 'array' as const,
                      message: `请填写${field.fieldLabel}`,
                    }
                  : { required: true, message: `请输入${field.fieldLabel}` },
              ]
            : undefined
        }
        valuePropName={field.widgetType === 'switch' ? 'checked' : 'value'}
      >
        {renderControl(resolved, fieldDisabled, runtime)}
      </Form.Item>
    );
  };

  // 仅渲染表单中可见字段（showInForm !== 0，undefined 视为显示；联动隐藏的字段不渲染但值保留）
  const visibleFields = fields
    .filter((field) => field.showInForm !== 0)
    .filter((field) =>
      isFieldVisible({ key: field.columnCode ?? '', linkage: field.linkage }, linkageValues),
    );

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
