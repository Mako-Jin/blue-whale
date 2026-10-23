// 设计器共享类型与工具（F6）：画布字段 uid、调色板载荷、dataType ↔ widgetType 映射
// 单列一个模块是为了让 Palette / Canvas / PropertyPanel / FormDesigner 共享类型而不产生循环依赖。

import type {
  BasicDataType,
  DataType,
  FieldConfig,
  Id,
  SubFieldConfig,
  WidgetType,
} from '../../api/form';

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

/** 子字段可选的基础存储类型（禁止嵌套子表/附件） */
export const BASIC_DATA_TYPES: readonly BasicDataType[] = [
  'text',
  'textarea',
  'number',
  'date',
  'datetime',
  'select',
  'bool',
];

/** 基础存储类型 → 中文标签（子表子字段编辑用） */
export const BASIC_TYPE_LABELS: Record<BasicDataType, string> = {
  text: '文本',
  textarea: '多行文本',
  number: '数字',
  date: '日期',
  datetime: '日期时间',
  select: '下拉',
  bool: '布尔',
};

/** 存储类型 → 中文标签（调色板展示用） */
export const DATA_TYPE_LABELS: Record<DataType, string> = {
  ...BASIC_TYPE_LABELS,
  attachment: '附件',
  subtable: '子表',
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

/** 存储类型 → 默认控件类型（bool→switch，其余同名回落；select→select；附件/子表同名） */
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
    case 'attachment':
      return ['attachment'];
    case 'subtable':
      return ['subtable'];
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
  attachment: '附件',
  subtable: '子表',
  title: '标题',
  divider: '分割线',
  'text-note': '说明',
};

/** 读取 validateJson 中的扩展配置（非法 JSON 或非对象返回空配置） */
export function parseFieldExtras(validateJson?: string): Partial<FieldConfig> {
  if (!validateJson) {
    return {};
  }
  try {
    const parsed: unknown = JSON.parse(validateJson);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    const extra = parsed as Record<string, unknown>;
    const result: Partial<FieldConfig> = {};
    if (typeof extra.multiple === 'boolean') {
      result.multiple = extra.multiple;
    }
    if (typeof extra.accept === 'string') {
      result.accept = extra.accept;
    }
    if (typeof extra.maxCount === 'number') {
      result.maxCount = extra.maxCount;
    }
    if (Array.isArray(extra.children)) {
      result.children = extra.children as SubFieldConfig[];
    }
    if (typeof extra.minRows === 'number') {
      result.minRows = extra.minRows;
    }
    if (typeof extra.maxRows === 'number') {
      result.maxRows = extra.maxRows;
    }
    return result;
  } catch {
    return {};
  }
}

/** 服务端字段 → 可编辑字段：把 validateJson 扩展配置展开为字段属性（字段显式值优先） */
export function hydrateField(field: FieldConfig): FieldConfig {
  return { ...parseFieldExtras(field.validateJson), ...field };
}

/** 保存前：按字段类型把扩展配置序列化到 validateJson（其他类型保留原值） */
export function buildValidateJson(field: FieldConfig): string | undefined {
  if (field.dataType === 'attachment' || field.widgetType === 'attachment') {
    return JSON.stringify({
      multiple: field.multiple !== false,
      accept: field.accept ?? '',
      maxCount: field.maxCount ?? 0,
    });
  }
  if (field.dataType === 'subtable' || field.widgetType === 'subtable') {
    return JSON.stringify({
      children: (field.children ?? []).map((child) => ({
        key: child.key,
        label: child.label,
        type: child.type,
        required: child.required === true,
        ...(child.maxLength === undefined ? {} : { maxLength: child.maxLength }),
        ...(child.options === undefined ? {} : { options: child.options }),
      })),
      minRows: field.minRows ?? 0,
      maxRows: field.maxRows ?? 0,
    });
  }
  return field.validateJson;
}

/** 新建附件字段的默认扩展配置 */
export function defaultAttachmentExtras(): Pick<FieldConfig, 'multiple' | 'maxCount'> {
  return { multiple: true, maxCount: 0 };
}

/** 新建子表字段的默认扩展配置 */
export function defaultSubtableExtras(): Pick<FieldConfig, 'children' | 'minRows' | 'maxRows'> {
  return { children: [], minRows: 0, maxRows: 0 };
}
