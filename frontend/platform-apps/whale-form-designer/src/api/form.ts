// whale-form 契约层（F1 冻结）：类型字段名与后端 DTO/VO 严格一致，禁止改名；
// 所有请求方法均为对 http.ts 的薄封装，路径前缀 /form/table-definition、/form/definition、/form/data。

import { del, get, post, put, type PageResult } from './http';

// 重要：后端 whale-common JacksonConfig 对 Long 统一走 ToStringSerializer，
// 雪花 ID 在 JSON 中一律为字符串。前端**必须**按 string 处理，
// 否则 Number(id) 会丢精度（>2^53）导致详情/删除等请求命中不存在的 ID（404）。
/** 后端 Long 类主键/外键，统一以字符串承载 */
export type Id = string;

/** 列存储类型 */
export type DataType = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'bool';

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
};
