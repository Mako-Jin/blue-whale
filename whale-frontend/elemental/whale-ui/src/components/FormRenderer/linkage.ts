// 表单字段联动（渲染侧）：纯函数、无 React 依赖，供 FormRenderer 与外部复用。
// 规则由后端编译进 GET /form/definition/{id} 的 schema.fields[].linkage。

/** 联动条件操作符 */
export type LinkageOperator = 'eq' | 'ne' | 'in' | 'notIn' | 'gt' | 'lt' | 'empty' | 'notEmpty';

/** 联动动作 */
export type LinkageAction = 'show' | 'hide' | 'require' | 'disable';

/** 单条联动条件 */
export interface LinkageCondition {
  /** 条件依赖的字段 key（即提交键 columnCode） */
  field: string;
  operator: LinkageOperator;
  /** 比较值；in/notIn 为逗号分隔字符串，empty/notEmpty 忽略 */
  value?: string | number;
}

/** 字段联动规则 */
export interface LinkageRule {
  action: LinkageAction;
  /** 组合方式，缺省 and */
  logic?: 'and' | 'or';
  conditions: LinkageCondition[];
}

/** 可参与联动判断的最小字段结构 */
interface LinkageField {
  key: string;
  required?: boolean;
  linkage?: LinkageRule;
}

/** 是否空值：null / undefined / '' / 空数组 */
function isEmptyValue(value: unknown): boolean {
  if (value === null || value === undefined) {
    return true;
  }
  if (typeof value === 'string') {
    return value === '';
  }
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  return false;
}

/** 是否可参与数值比较（非数字一律不成立） */
function toComparableNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

/** 字符串比较前归一：null/undefined 视为空串 */
function toComparableText(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

/** 解析 in/notIn 的逗号分隔值（去空白、丢弃空项） */
function toValueSet(value: unknown): Set<string> {
  const raw = value === null || value === undefined ? '' : String(value);
  return new Set(
    raw
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item !== ''),
  );
}

/** 单条条件判断（未知操作符一律不成立） */
function evaluateCondition(condition: LinkageCondition, values: Record<string, unknown>): boolean {
  if (!condition || typeof condition.field !== 'string') {
    return false;
  }
  const current = values[condition.field];
  switch (condition.operator) {
    case 'eq':
      return toComparableText(current) === toComparableText(condition.value);
    case 'ne':
      return toComparableText(current) !== toComparableText(condition.value);
    case 'in':
      return toValueSet(condition.value).has(toComparableText(current));
    case 'notIn':
      return !toValueSet(condition.value).has(toComparableText(current));
    case 'gt': {
      const left = toComparableNumber(current);
      const right = toComparableNumber(condition.value);
      return left !== null && right !== null && left > right;
    }
    case 'lt': {
      const left = toComparableNumber(current);
      const right = toComparableNumber(condition.value);
      return left !== null && right !== null && left < right;
    }
    case 'empty':
      return isEmptyValue(current);
    case 'notEmpty':
      return !isEmptyValue(current);
    default:
      return false;
  }
}

/** 联动条件是否成立：无规则 / 条件为空 / 未知操作符均返回 false */
export function evaluateLinkage(
  rule: LinkageRule | undefined | null,
  values: Record<string, unknown>,
): boolean {
  if (!rule || !Array.isArray(rule.conditions) || rule.conditions.length === 0) {
    return false;
  }
  const source = values ?? {};
  return rule.logic === 'or'
    ? rule.conditions.some((condition) => evaluateCondition(condition, source))
    : rule.conditions.every((condition) => evaluateCondition(condition, source));
}

/** 字段是否可见：无规则恒可见；show 条件成立显示、hide 条件成立隐藏 */
export function isFieldVisible(
  field: Pick<LinkageField, 'key' | 'linkage'>,
  values: Record<string, unknown>,
): boolean {
  if (!field.linkage) {
    return true;
  }
  const matched = evaluateLinkage(field.linkage, values);
  if (field.linkage.action === 'show') {
    return matched;
  }
  if (field.linkage.action === 'hide') {
    return !matched;
  }
  return true;
}

/** 字段是否因联动被禁用：action=disable 且条件成立时为 true */
export function isFieldDisabled(
  field: Pick<LinkageField, 'key' | 'linkage'>,
  values: Record<string, unknown>,
): boolean {
  const rule = field.linkage;
  return !!rule && rule.action === 'disable' && evaluateLinkage(rule, values);
}

/** 字段是否必填：原有 required 与 action=require 的联动结果取或 */
export function isFieldRequired(
  field: Pick<LinkageField, 'key' | 'required' | 'linkage'>,
  values: Record<string, unknown>,
): boolean {
  if (field.required === true) {
    return true;
  }
  const rule = field.linkage;
  return !!rule && rule.action === 'require' && evaluateLinkage(rule, values);
}
