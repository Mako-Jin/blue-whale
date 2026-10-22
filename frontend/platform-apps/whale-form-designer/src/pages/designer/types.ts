// 设计器共享类型与工具（F6）：画布字段 uid、调色板载荷、dataType ↔ widgetType 映射
// 单列一个模块是为了让 Palette / Canvas / PropertyPanel / FormDesigner 共享类型而不产生循环依赖。

import type { DataType, FieldConfig, Id, WidgetType } from '../../api/form';

/** 画布字段：在契约 FieldConfig 基础上追加前端稳定 uid（作为 @dnd-kit 的排序键） */
export interface DesignerField extends FieldConfig {
  /** 前端稳定标识，仅用于拖拽与 React key；保存时剥离 */
  _uid: string;
}

/** 调色板条目拖入画布时携带的载荷 */
export interface PalettePayload {
  /** 关联列元数据 ID；null 为纯布局字段 */
  columnMetaId: Id | null;
  /** 默认显示名 */
  fieldLabel: string;
  /** 默认控件类型 */
  widgetType: WidgetType;
}

/** 画布容器 droppable 的固定 id（Palette 拖入空白区域时作为落点） */
export const CANVAS_DROPPABLE_ID = 'designer-canvas';

/** 纯布局控件集合（不绑定表单值、不参与提交） */
export const LAYOUT_WIDGETS: readonly WidgetType[] = ['title', 'divider', 'text-note'];

/** 存储类型 → 中文标签（调色板展示用） */
export const DATA_TYPE_LABELS: Record<DataType, string> = {
  text: '文本',
  textarea: '多行文本',
  number: '数字',
  date: '日期',
  datetime: '日期时间',
  select: '下拉',
  bool: '布尔',
};

/** 布局控件默认文案 */
export const LAYOUT_LABELS: Record<string, string> = {
  title: '标题',
  divider: '分割线',
  'text-note': '说明文字',
};

/** 是否为纯布局字段（关联列为空且控件为布局控件） */
export function isLayoutField(field: FieldConfig): boolean {
  return (
    field.columnMetaId == null ||
    (field.widgetType != null && LAYOUT_WIDGETS.includes(field.widgetType))
  );
}

/** 存储类型 → 默认控件类型（bool→switch，其余同名回落；select→select） */
export function defaultWidget(dataType: DataType | undefined): WidgetType {
  if (dataType === 'bool') {
    return 'switch';
  }
  if (dataType === undefined) {
    return 'text';
  }
  return dataType;
}

/** 存储类型 → 可选控件候选（属性面板下拉） */
export function widgetCandidates(dataType: DataType | undefined): WidgetType[] {
  switch (dataType) {
    case 'text':
      return ['text', 'textarea'];
    case 'textarea':
      return ['textarea'];
    case 'number':
      return ['number'];
    case 'select':
      return ['select', 'radio', 'checkbox'];
    case 'date':
      return ['date'];
    case 'datetime':
      return ['datetime'];
    case 'bool':
      return ['switch'];
    default:
      return ['text'];
  }
}

/** 生成前端稳定 uid */
export function nextUid(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** 控件类型 → 中文标签（画布与属性面板展示用） */
export const WIDGET_LABELS: Record<WidgetType, string> = {
  text: '单行文本',
  textarea: '多行文本',
  number: '数字',
  select: '下拉框',
  radio: '单选',
  checkbox: '多选',
  date: '日期',
  datetime: '日期时间',
  switch: '开关',
  title: '标题',
  divider: '分割线',
  'text-note': '说明',
};
