// 动态条件工具（F8）：类型-操作符映射、多值草稿处理、条件序列化与展示格式化。
// 从 FormDataList 拆出的纯函数模块，供容器组件、动态条件 Hook 与筛选面板共享。

import dayjs, { type Dayjs } from 'dayjs';
import type { DataFilter, DataType, FieldConfig, FilterOp } from '../../../api/form';

/** 支持动态条件检索的列类型（后端 FormDataType.isFilterable 白名单） */
export const FILTERABLE_TYPES = new Set<DataType>([
  'text',
  'textarea',
  'number',
  'date',
  'datetime',
  'select',
  'bool',
]);

/** in 条件值上限（与后端 MAX_IN_VALUES 对齐） */
export const MAX_IN_VALUES = 50;

/** 操作符中文名 */
export const FILTER_OP_LABELS: Record<FilterOp, string> = {
  eq: '等于',
  ne: '不等于',
  gt: '大于',
  gte: '大于等于',
  lt: '小于',
  lte: '小于等于',
  like: '包含',
  in: '属于',
  between: '介于',
  isNull: '为空',
  notNull: '不为空',
};

/** 按列类型给出可用操作符（与后端类型-操作符校验一致） */
export function filterOpsFor(dataType: DataType | undefined): FilterOp[] {
  switch (dataType) {
    case 'number':
    case 'date':
    case 'datetime':
      return ['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'in', 'between', 'isNull', 'notNull'];
    case 'bool':
      return ['eq', 'ne', 'in', 'isNull', 'notNull'];
    default:
      return ['eq', 'ne', 'like', 'in', 'isNull', 'notNull'];
  }
}

/** 操作符是否需要填写值 */
export function needsValue(op: FilterOp): boolean {
  return op !== 'isNull' && op !== 'notNull';
}

/** 是否多值操作符（in 集合 / between 区间），值控件走独立分支 */
export function isMultiValueOp(op: FilterOp): boolean {
  return op === 'in' || op === 'between';
}

/** 多值草稿清洗：统一转字符串并去首尾空白 */
export function cleanDraftValues(values: Array<string | number>): string[] {
  return values.map((value) => String(value).trim());
}

/** in 条件值去空去重（保持输入顺序） */
export function uniqueDraftValues(values: string[]): string[] {
  return Array.from(new Set(values.filter((value) => value.length > 0)));
}

/** 按字段类型转换多值条件：number 转数值，其余保留文本；非法返回 null */
export function buildMultiValues(
  dataType: DataType | undefined,
  values: string[],
): Array<string | number> | null {
  if (dataType !== 'number') {
    return values;
  }
  const numbers = values.map((value) => Number(value));
  return numbers.some((value) => Number.isNaN(value)) ? null : numbers;
}

/** 动态条件序列化为查询参数（空条件返回 undefined，避免请求携带无用字段） */
export function serializeFilters(filters: DataFilter[]): string | undefined {
  return filters.length > 0 ? JSON.stringify(filters) : undefined;
}

/** 按列编码取字段中文名（缺失时回落编码） */
export function fieldLabelOf(fields: FieldConfig[], code: string): string {
  return fields.find((field) => field.columnCode === code)?.fieldLabel || code;
}

/** 空值统一显示为短横线，其余原样字符串化（checkbox 的逗号串不拆分） */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

/** 条件值展示：多值按操作符连接（between 用 ~，in 用逗号） */
export function formatFilterValue(filter: DataFilter): string {
  if (Array.isArray(filter.value)) {
    const separator = filter.op === 'between' ? ' ~ ' : ', ';
    return filter.value.map((item) => formatValue(item)).join(separator);
  }
  return formatValue(filter.value);
}

/** 日期字符串转 dayjs（空串/非法格式返回 undefined，避免 DatePicker 报错） */
export function toDayjs(value: string | number | undefined): Dayjs | undefined {
  if (typeof value !== 'string' || value.trim() === '') {
    return undefined;
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed : undefined;
}
