// FormRenderer 控件渲染分发：纯布局字段渲染 + 按控件类型渲染数据字段控件（disabled 已由调用方合并）

import {
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
import { AttachmentControl } from './AttachmentControl';
import { type FileRuntime } from './fileRuntime';
import { SubtableControl } from './SubtableControl';
import { toOptions } from './values';
import type { WhaleFormFieldConfig } from './types';

/** 渲染纯布局字段（文本统一取 fieldLabel） */
export function renderLayout(field: WhaleFormFieldConfig) {
  if (field.widgetType === 'divider') {
    return <Divider />;
  }
  if (field.widgetType === 'text-note') {
    return <Typography.Text type="secondary">{field.fieldLabel}</Typography.Text>;
  }
  return <Typography.Title level={5}>{field.fieldLabel}</Typography.Title>;
}

/** 渲染数据字段控件（disabled 已由调用方合并「整体禁用 + editable=0」） */
export function renderControl(field: WhaleFormFieldConfig, disabled: boolean, runtime: FileRuntime) {
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
    case 'attachment':
      return <AttachmentControl field={field} disabled={disabled} runtime={runtime} />;
    case 'subtable':
      return <SubtableControl field={field} disabled={disabled} />;
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
