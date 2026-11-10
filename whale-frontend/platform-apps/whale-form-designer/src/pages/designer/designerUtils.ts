// 设计器纯函数工具（F6）：画布字段 ↔ 保存请求字段转换、保存前校验、调色板载荷 → 画布字段。
// 从 FormDesigner 拆出的无状态模块，供容器组件复用。

import type { ColumnMeta, FieldConfig, Id } from '../../api/form';
import {
  buildValidateJson,
  defaultAttachmentExtras,
  defaultSubtableExtras,
  isLayoutField,
  nextUid,
  type DesignerField,
  type PalettePayload,
} from './types';

/** 画布字段 → 保存请求字段（剥离前端 _uid 与冗余列信息；sortNo 以画布顺序为准） */
export function toSaveField(field: DesignerField, index: number): FieldConfig {
  const layout = isLayoutField(field);
  return {
    columnMetaId: field.columnMetaId ?? null,
    fieldLabel: field.fieldLabel,
    widgetType: field.widgetType,
    showInList: field.showInList ?? 1,
    showInForm: field.showInForm ?? 1,
    editable: layout ? 1 : (field.editable ?? 1),
    sortNo: index,
    colSpan: field.colSpan ?? 24,
    placeholder: layout ? undefined : field.placeholder,
    defaultValue: layout ? undefined : field.defaultValue,
    validateJson: buildValidateJson(field),
    remark: field.remark,
    status: field.status ?? 1,
    options: layout ? undefined : field.options,
  };
}

/** 保存前画布校验：至少一个数据字段、同列不重复、显示名非空 */
export function validateCanvas(fields: DesignerField[]): string | null {
  const dataFields = fields.filter((field) => !isLayoutField(field));
  if (dataFields.length === 0) {
    return '请至少拖入一个数据字段（纯布局字段不构成表单数据）';
  }
  const ids = dataFields.map((field) => field.columnMetaId as Id);
  if (new Set(ids).size !== ids.length) {
    return '同一列被重复引用，请移除重复的数据字段';
  }
  if (fields.some((field) => !field.fieldLabel || !field.fieldLabel.trim())) {
    return '存在未填写显示名的字段';
  }
  return null;
}

/** 调色板载荷 → 画布字段（回填关联列信息，便于预览与属性面板只读展示） */
export function buildField(
  payload: PalettePayload,
  columnsById: Map<Id, ColumnMeta>,
): DesignerField {
  const column = payload.columnMetaId != null ? columnsById.get(payload.columnMetaId) : undefined;
  const isComposite = payload.widgetType === 'attachment' || payload.widgetType === 'subtable';
  return {
    _uid: nextUid(),
    columnMetaId: payload.columnMetaId,
    fieldLabel: payload.fieldLabel,
    widgetType: payload.widgetType,
    showInList: 1,
    showInForm: 1,
    editable: 1,
    colSpan: 24,
    status: 1,
    placeholder:
      column && !isComposite ? `请输入${column.columnName || column.columnCode}` : undefined,
    options:
      payload.widgetType === 'select' ||
      payload.widgetType === 'radio' ||
      payload.widgetType === 'checkbox'
        ? []
        : undefined,
    columnCode: column?.columnCode,
    dataType: column?.dataType,
    required: column?.required,
    maxLength: column?.length,
    // 复合字段的扩展配置，保存时序列化到 validateJson
    ...(payload.widgetType === 'attachment' ? defaultAttachmentExtras() : {}),
    ...(payload.widgetType === 'subtable' ? defaultSubtableExtras() : {}),
  };
}
