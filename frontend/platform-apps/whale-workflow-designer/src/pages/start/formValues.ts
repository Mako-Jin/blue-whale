// 发起页表单工具：必填校验与提交前序列化（等价于 whale-form-designer src/render/FormRender.tsx 的判定逻辑，
// 但按应用边界在本应用内独立实现，不跨应用 import）。

import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import type { FieldConfig, WidgetType } from '../../api/form';

/** 纯布局控件集合（无关联列，不参与提交） */
const LAYOUT_WIDGETS: readonly WidgetType[] = ['title', 'divider', 'text-note'];

/** 是否纯布局字段 */
export function isLayoutWidget(widgetType?: WidgetType): boolean {
  return widgetType !== undefined && LAYOUT_WIDGETS.includes(widgetType);
}

/** 空值判定：undefined / null / 空字符串 / 空数组视为未填 */
export function isEmptyValue(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return true;
  }
  return Array.isArray(value) && value.length === 0;
}

/** 首个未填的必填数据字段（提交按钮在 FormRenderer 之外，故需提交前自检） */
export function findMissingRequired(
  fields: FieldConfig[],
  values: Record<string, unknown>,
): FieldConfig | undefined {
  return fields.find((field) => {
    const code = field.columnCode;
    return (
      field.required === 1 &&
      code !== undefined &&
      !isLayoutWidget(field.widgetType) &&
      isEmptyValue(values[code])
    );
  });
}

/** 提交前序列化：仅数据字段，键为 columnCode，空值跳过（日期/多选/开关规则与表单设计器一致） */
export function serializeValues(
  fields: FieldConfig[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  fields.forEach((field) => {
    const code = field.columnCode;
    if (!code || isLayoutWidget(field.widgetType)) {
      return;
    }
    const current = values[code];
    if (isEmptyValue(current)) {
      return;
    }
    switch (field.widgetType) {
      case 'date':
        payload[code] = dayjs(current as string | number | Date | Dayjs).format('YYYY-MM-DD');
        break;
      case 'datetime':
        payload[code] = dayjs(current as string | number | Date | Dayjs).format(
          'YYYY-MM-DD HH:mm:ss',
        );
        break;
      case 'checkbox':
        payload[code] = Array.isArray(current) ? current.join(',') : String(current);
        break;
      case 'switch':
        payload[code] = current ? 1 : 0;
        break;
      case 'number':
        payload[code] = Number(current);
        break;
      default:
        // text / textarea / select / radio
        payload[code] = String(current);
    }
  });
  return payload;
}

/** 浅比较：受控值回写时未变化则保留原引用，避免无谓重渲染 */
export function isSameValues(
  previous: Record<string, unknown>,
  next: Record<string, unknown>,
): boolean {
  const keys = new Set([...Object.keys(previous), ...Object.keys(next)]);
  for (const key of keys) {
    if (previous[key] !== next[key]) {
      return false;
    }
  }
  return true;
}
