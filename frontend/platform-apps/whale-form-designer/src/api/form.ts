// whale-form 契约层（F1 冻结）：类型字段名与后端 DTO/VO 严格一致，禁止改名；
// 所有请求方法均为对 http.ts 的薄封装，路径前缀 /form/table-definition、/form/definition、/form/data。

import { del, downloadBlob, get, post, put, type PageResult } from './client';

// 重要：后端 whale-common JacksonConfig 对 Long 统一走 ToStringSerializer，
// 雪花 ID 在 JSON 中一律为字符串。前端**必须**按 string 处理，
// 否则 Number(id) 会丢精度（>2^53）导致详情/删除等请求命中不存在的 ID（404）。
/** 后端 Long 类主键/外键，统一以字符串承载 */
export type Id = string;

/** 基础列存储类型（子表子字段仅允许这些类型） */
export type BasicDataType =
  'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool';

/** 列存储类型（attachment / subtable 值以 JSON 数组落 TEXT 列） */
export type DataType = BasicDataType | 'attachment' | 'subtable';

/** 控件类型；后 3 个（title/divider/text-note）为纯布局控件 */
export type WidgetType =
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

/** 列元数据（对应后端 FormColumnMetaDTO / FormColumnMetaVO） */
export interface ColumnMeta {
  id?: Id;
  columnCode: string;
  columnName: string;
  dataType: DataType;
  length?: number;
  precision?: number;
  scale?: number;
  nullable?: number;
  required?: number;
  defaultValue?: string;
  remark?: string;
  sortNo?: number;
  status?: number;
}

/** 表定义（对应后端 FormTableDefinitionSaveRequest / FormTableDefinitionVO） */
export interface TableDefinition {
  id?: Id;
  tableCode: string;
  tableName: string;
  version?: number;
  remark?: string;
  status?: number;
  columns?: ColumnMeta[];
}

/** 字段选项（对应后端 FormFieldOptionDTO / FormFieldOptionVO） */
export interface FieldOption {
  optionLabel: string;
  optionValue: string;
  sortNo?: number;
}

/** 子表子字段定义（持久化在 validateJson.children，仅基础类型） */
export interface SubFieldConfig {
  key: string;
  label: string;
  type: BasicDataType;
  required?: boolean;
  maxLength?: number;
  options?: string[];
}

/** 表单字段配置（对应后端 FormFieldConfigDTO / FormFieldConfigVO） */
export interface FieldConfig {
  id?: Id;
  columnMetaId?: Id | null;
  fieldLabel: string;
  widgetType?: WidgetType;
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
  options?: FieldOption[];
  // 以下为关联列信息冗余回填（来自后端 enrich；columnMetaId 为 null 的纯布局字段为 undefined）
  columnCode?: string;
  dataType?: DataType;
  required?: number;
  maxLength?: number;
  // 附件扩展配置（保存时序列化到 validateJson）
  multiple?: boolean;
  accept?: string;
  maxCount?: number;
  // 子表扩展配置（保存时序列化到 validateJson）
  children?: SubFieldConfig[];
  minRows?: number;
  maxRows?: number;
}

/** 表单定义（对应后端 FormDefinitionSaveRequest / FormDefinitionVO） */
export interface FormDefinition {
  id?: Id;
  code: string;
  name: string;
  tableDefinitionId?: Id;
  schemaJson?: string;
  version?: number;
  status?: number;
  remark?: string;
  fields?: FieldConfig[];
}

/** 表单数据（对应后端 FormDataVO） */
export interface FormDataItem {
  id: Id;
  definitionId: Id;
  definitionCode: string;
  definitionVersion: number;
  bizKey?: string;
  dataJson: string;
  fields?: Record<string, unknown> | null;
  submitter?: string;
  status: number;
  createTime: string;
}

/** 分页查询参数：current/size 为通用分页（对齐后端 PageQuery），其余键为过滤条件（如 code/name/status/definitionCode/bizKey） */
export interface PageParams {
  current?: number;
  size?: number;
  [key: string]: unknown;
}

/** 提交表单数据入参（POST /form/data）：dataJson 为 JSON 文本，键必须为 schema 内的字段 */
export interface FormDataSubmitParams {
  definitionId: Id;
  dataJson: string;
  bizKey?: string;
}

/** 导出格式：csv（text/csv）/ xlsx（Excel） */
export type ExportFormat = 'csv' | 'xlsx';

/** 表定义 API（服务内前缀 /form/table-definition） */
export const tableDefinitionApi = {
  page: (query?: PageParams) =>
    get<PageResult<TableDefinition>>('/form/table-definition/page', query),
  detail: (id: Id) => get<TableDefinition>(`/form/table-definition/${id}`),
  create: (data: TableDefinition) => post<TableDefinition>('/form/table-definition', data),
  update: (id: Id, data: TableDefinition) =>
    put<TableDefinition>(`/form/table-definition/${id}`, data),
  remove: (id: Id) => del<void>(`/form/table-definition/${id}`),
  publish: (id: Id) => post<void>(`/form/table-definition/${id}/publish`),
};

/** 表单视图 API（服务内前缀 /form/definition） */
export const formApi = {
  page: (query?: PageParams) => get<PageResult<FormDefinition>>('/form/definition/page', query),
  detail: (id: Id) => get<FormDefinition>(`/form/definition/${id}`),
  create: (data: FormDefinition) => post<FormDefinition>('/form/definition', data),
  update: (id: Id, data: FormDefinition) => put<FormDefinition>(`/form/definition/${id}`, data),
  remove: (id: Id) => del<void>(`/form/definition/${id}`),
  publish: (id: Id) => post<void>(`/form/definition/${id}/publish`),
  // 下拉数据源：列出表定义（复用后端 GET /form/table-definition/page）
  tables: (query?: PageParams) =>
    get<PageResult<TableDefinition>>('/form/table-definition/page', query),
};

/** 表单数据 API（服务内前缀 /form/data） */
export const formDataApi = {
  page: (query?: PageParams) => get<PageResult<FormDataItem>>('/form/data/page', query),
  detail: (id: Id) => get<FormDataItem>(`/form/data/${id}`),
  create: (data: FormDataSubmitParams) => post<FormDataItem>('/form/data', data),
  remove: (id: Id) => del<void>(`/form/data/${id}`),
  // 导出为原始字节流（响应非 JSON 信封），definitionCode 必填
  export: (query: PageParams & { definitionCode: string; format?: ExportFormat }) =>
    downloadBlob('/form/data/export', query),
};
