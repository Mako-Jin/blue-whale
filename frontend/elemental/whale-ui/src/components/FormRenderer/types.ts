// FormRenderer 类型定义：与后端 DTO/VO 字段名严格一致（表单设计器「预览」与工作流填报/审批页共用）

import type { LinkageRule } from './linkage';

/** 后端 Long 类主键/外键，统一以字符串承载（雪花 ID 在 JSON 中一律为字符串） */
export type WhaleFormId = string;

/** 基础列存储类型（子表子字段仅允许这些类型） */
export type WhaleFormBasicDataType =
  'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool';

/** 列存储类型（与后端 DTO/VO 字段名保持一致） */
export type WhaleFormDataType = WhaleFormBasicDataType | 'attachment' | 'subtable';

/** 控件类型；后 3 个（title/divider/text-note）为纯布局控件 */
export type WhaleFormWidgetType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'select'
  | 'radio'
  | 'checkbox'
  | 'date'
  | 'datetime'
  | 'switch'
  | 'attachment'
  | 'subtable'
  | 'title'
  | 'divider'
  | 'text-note';

/** 字段选项（对应后端 FormFieldOptionDTO / FormFieldOptionVO） */
export interface WhaleFormFieldOption {
  optionLabel: string;
  optionValue: string;
  sortNo?: number;
}

/** 子表子字段定义（与 schema_json children 元素结构一致） */
export interface WhaleFormSubField {
  key: string;
  label: string;
  type: WhaleFormBasicDataType;
  required?: boolean;
  maxLength?: number;
  options?: string[];
}

/** 已上传文件（上传接口返回后回显文件名与大小；仅文件 ID 落值） */
export interface WhaleUploadedFile {
  id: string;
  name?: string;
  size?: number;
}

/** 表单字段配置（对应后端 FormFieldConfigDTO / FormFieldConfigVO，字段名严格一致） */
export interface WhaleFormFieldConfig {
  id?: WhaleFormId;
  columnMetaId?: WhaleFormId | null;
  fieldLabel: string;
  widgetType?: WhaleFormWidgetType;
  showInList?: number;
  showInForm?: number;
  editable?: number;
  sortNo?: number;
  colSpan?: number;
  placeholder?: string;
  defaultValue?: string;
  validateJson?: string;
  remark?: string;
  status?: number;
  options?: WhaleFormFieldOption[];
  // 关联列信息冗余回填（来自后端 enrich；columnMetaId 为 null 的纯布局字段为 undefined）
  columnCode?: string;
  dataType?: WhaleFormDataType;
  required?: number;
  maxLength?: number;
  // 附件扩展配置（也可由 validateJson 解析得到）
  multiple?: boolean;
  accept?: string;
  maxCount?: number;
  // 子表扩展配置（也可由 validateJson 解析得到）
  children?: WhaleFormSubField[];
  minRows?: number;
  maxRows?: number;
  // 字段联动规则（仅当存在时参与显示/必填/禁用判断）
  linkage?: LinkageRule;
}

/** 渲染器入参（设计器预览与填报页共用） */
export interface FormRendererProps {
  /** 字段配置，数组顺序即渲染顺序 */
  fields: WhaleFormFieldConfig[];
  /** 受控值，键为 columnCode */
  value?: Record<string, unknown>;
  /** 值变化回调：回传全量字段值（switch 已由 boolean 转回 1/0） */
  onChange?: (values: Record<string, unknown>) => void;
  /** 整体禁用（只读预览等） */
  disabled?: boolean;
  /** 上传接口 Bearer 令牌（为空则不带鉴权头） */
  authToken?: string;
  /** 文件服务基地址（默认同源；开发期由 vite proxy 转发 /file） */
  apiBaseUrl?: string;
  /** 自定义上传（默认 POST {apiBaseUrl}/file/upload，multipart 字段名 file） */
  uploadFile?: (file: File) => Promise<WhaleUploadedFile>;
  /** 自定义下载（默认 GET {apiBaseUrl}/file/{id}/download） */
  downloadFile?: (file: WhaleUploadedFile) => void | Promise<void>;
}
