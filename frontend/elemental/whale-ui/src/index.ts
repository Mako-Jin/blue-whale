export { WhaleButton } from './button';
export type { WhaleButtonProps } from './button';
export { WhaleInput } from './input';
export type { WhaleInputProps } from './input';
export { WhaleModal } from './modal';
export type { WhaleModalProps } from './modal';
export { WhaleTable } from './table';
export type { WhaleTableProps } from './table';

// 业务组件二次封装（T5.22）
export { SearchForm } from './form/SearchForm';
export type { SearchFormItem, SearchFormProps } from './form/SearchForm';
export { FormModal } from './form/FormModal';
export type { FormModalProps } from './form/FormModal';
export { ProTable } from './table/ProTable';
export type { PageParams, PageResult, ProTableProps, ProTableRef } from './table/ProTable';

// 受控动态表单渲染器（表单设计器预览 + 工作流填报/审批页共用）
export { FormRenderer } from './components/FormRenderer';
export type {
  FormRendererProps,
  WhaleFormBasicDataType,
  WhaleFormDataType,
  WhaleFormFieldConfig,
  WhaleFormSubField,
  WhaleFormWidgetType,
  WhaleUploadedFile,
} from './components/FormRenderer';
// 表单字段联动规则类型（随字段 schema 一并对外暴露）
export type {
  LinkageAction,
  LinkageCondition,
  LinkageOperator,
  LinkageRule,
} from './components/FormRenderer/linkage';

// 按钮级权限（T5.23）
export { PermissionProvider, usePermission } from './permission/context';
export type { PermissionUtils, PermissionProviderProps } from './permission/context';
export { AuthButton } from './permission/AuthButton';
export type { AuthButtonProps } from './permission/AuthButton';

// 主题变量注入（Wave 5.33 item ②）：复用 whale-common-theme 的 --bw-* 令牌体系
export { buildThemeVars, applyThemeVars } from './theme';
export { tokens, antdThemeConfig, getAntdTheme } from './theme';
export type { ThemeTokens } from './theme';
