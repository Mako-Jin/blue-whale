// 设计器画布字段预览（F6）：在画布卡片内以禁用态渲染单个字段控件。
// 数据字段控件形态需与填报页 FormRenderer 保持一致，避免「所见非所得」。

import {
  Button,
  Checkbox,
  DatePicker,
  Divider,
  Input,
  InputNumber,
  Radio,
  Select,
  Switch,
  Typography,
} from 'antd';
import { LAYOUT_WIDGETS, type DesignerField } from './types';

/** FieldOption → antd options */
function toOptions(field: DesignerField) {
  return (field.options ?? []).map((option) => ({
    label: option.optionLabel,
    value: option.optionValue,
  }));
}

/** 数据字段控件（禁用态，仅作视觉预览） */
function renderDataControl(field: DesignerField) {
  switch (field.widgetType) {
    case 'textarea':
      return <Input.TextArea placeholder={field.placeholder} disabled rows={2} />;
    case 'number':
      return <InputNumber placeholder={field.placeholder} disabled style={{ width: '100%' }} />;
    case 'select':
      return <Select placeholder={field.placeholder} options={toOptions(field)} disabled />;
    case 'radio':
      return <Radio.Group options={toOptions(field)} disabled />;
    case 'checkbox':
      return <Checkbox.Group options={toOptions(field)} disabled />;
    case 'date':
      return (
        <DatePicker
          format="YYYY-MM-DD"
          placeholder={field.placeholder}
          disabled
          style={{ width: '100%' }}
        />
      );
    case 'datetime':
      return (
        <DatePicker
          showTime
          format="YYYY-MM-DD HH:mm:ss"
          placeholder={field.placeholder}
          disabled
          style={{ width: '100%' }}
        />
      );
    case 'switch':
      return <Switch disabled />;
    case 'attachment': {
      const limit =
        field.maxCount !== undefined && field.maxCount > 0
          ? `最多 ${field.maxCount} 个`
          : field.multiple === false
            ? '仅 1 个'
            : '支持多个';
      return (
        <div>
          <Button size="small" disabled>
            上传文件
          </Button>
          <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
            {limit}
          </Typography.Text>
        </div>
      );
    }
    case 'subtable': {
      const childCount = field.children?.length ?? 0;
      const rowHint =
        field.maxRows !== undefined && field.maxRows > 0 ? `最多 ${field.maxRows} 行` : '行数不限';
      return (
        <div>
          <Typography.Text type="secondary">
            子表：{childCount} 个子字段，{rowHint}
          </Typography.Text>
          <div style={{ marginTop: 4 }}>
            <Button size="small" type="dashed" disabled>
              新增行
            </Button>
          </div>
        </div>
      );
    }
    default:
      return <Input placeholder={field.placeholder} disabled />;
  }
}

export default function FieldPreview({ field }: { field: DesignerField }) {
  if (field.widgetType && LAYOUT_WIDGETS.includes(field.widgetType)) {
    if (field.widgetType === 'divider') {
      return <Divider style={{ margin: '4px 0' }} />;
    }
    if (field.widgetType === 'text-note') {
      return <Typography.Text type="secondary">{field.fieldLabel}</Typography.Text>;
    }
    return (
      <Typography.Title level={5} style={{ margin: 0 }}>
        {field.fieldLabel}
      </Typography.Title>
    );
  }
  return (
    <div>
      <div style={{ marginBottom: 4 }}>
        {field.fieldLabel}
        {field.required === 1 ? <span style={{ color: '#ff4d4f', marginLeft: 4 }}>*</span> : null}
      </div>
      {renderDataControl(field)}
    </div>
  );
}
