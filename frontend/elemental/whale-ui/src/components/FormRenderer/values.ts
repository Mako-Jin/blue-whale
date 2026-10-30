// FormRenderer 值转换纯函数：布局字段判定、选项映射、validateJson 扩展配置解析、内外部值双向转换

import dayjs from 'dayjs';
import type { WhaleFormFieldConfig, WhaleFormSubField, WhaleFormWidgetType } from './types';

/** 纯布局控件集合 */
const LAYOUT_WIDGETS: readonly WhaleFormWidgetType[] = ['title', 'divider', 'text-note'];

/** 是否纯布局字段：无关联列、不绑定表单值、不参与提交 */
export function isLayoutWidget(widgetType?: WhaleFormWidgetType): boolean {
  return widgetType !== undefined && LAYOUT_WIDGETS.includes(widgetType);
}

/** FieldOption → antd options */
export function toOptions(field: WhaleFormFieldConfig): { label: string; value: string }[] {
  return (field.options ?? []).map((option) => ({
    label: option.optionLabel,
    value: option.optionValue,
  }));
}

/** 把 validateJson 中的扩展配置合并进字段（字段本身有声明的值优先） */
export function resolveFieldExtras(field: WhaleFormFieldConfig): WhaleFormFieldConfig {
  if (!field.validateJson) {
    return field;
  }
  let extra: Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(field.validateJson);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return field;
    }
    extra = parsed as Record<string, unknown>;
  } catch {
    return field;
  }
  const children = Array.isArray(extra.children)
    ? (extra.children as WhaleFormSubField[])
    : undefined;
  return {
    ...field,
    multiple: field.multiple ?? (typeof extra.multiple === 'boolean' ? extra.multiple : undefined),
    accept: field.accept ?? (typeof extra.accept === 'string' ? extra.accept : undefined),
    maxCount: field.maxCount ?? (typeof extra.maxCount === 'number' ? extra.maxCount : undefined),
    children: field.children ?? children,
    minRows: field.minRows ?? (typeof extra.minRows === 'number' ? extra.minRows : undefined),
    maxRows: field.maxRows ?? (typeof extra.maxRows === 'number' ? extra.maxRows : undefined),
  };
}

/** 外部值 → 内部值：switch 的 1/0 转 boolean；日期字符串转 dayjs；多选字符串转数组 */
export function toInternalValues(
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
    } else if (field.widgetType === 'attachment') {
      // 文件 ID 统一按字符串承载，避免雪花 ID 经 Number() 丢精度
      internal[code] = Array.isArray(current) ? current.map((id) => String(id)) : current;
    } else {
      internal[code] = current;
    }
  });
  return internal;
}

/** 内部值 → 外部值：switch 的 boolean 转回 1/0 */
export function toExternalValues(
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
