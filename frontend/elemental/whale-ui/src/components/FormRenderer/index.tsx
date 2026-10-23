// FormRenderer - 受控动态表单渲染器（表单设计器「预览」与工作流填报/审批页共用）
// 数据源为 GET /form/definition/{id} 的 fields[]（天然含纯布局字段），提交键 = columnCode。
// 纯布局控件 title / divider / text-note 只渲染、不绑定表单值。
// 复合字段：attachment（文件 ID 数组）与 subtable（行对象数组），扩展配置可来自 validateJson。

import { useEffect, useRef, useState } from 'react';
import {
  Button,
  Checkbox,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';

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

/** 文件上传/下载运行时配置 */
interface FileRuntime {
  authToken?: string;
  apiBaseUrl?: string;
  uploadFile?: FormRendererProps['uploadFile'];
  downloadFile?: FormRendererProps['downloadFile'];
}

/** 纯布局控件集合 */
const LAYOUT_WIDGETS: readonly WhaleFormWidgetType[] = ['title', 'divider', 'text-note'];

/** 是否纯布局字段：无关联列、不绑定表单值、不参与提交 */
function isLayoutWidget(widgetType?: WhaleFormWidgetType): boolean {
  return widgetType !== undefined && LAYOUT_WIDGETS.includes(widgetType);
}

/** FieldOption → antd options */
function toOptions(field: WhaleFormFieldConfig): { label: string; value: string }[] {
  return (field.options ?? []).map((option) => ({
    label: option.optionLabel,
    value: option.optionValue,
  }));
}

/** 把 validateJson 中的扩展配置合并进字段（字段本身有声明的值优先） */
function resolveFieldExtras(field: WhaleFormFieldConfig): WhaleFormFieldConfig {
  if (!field.validateJson) {
    return field;
  }
  let extra: Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(field.validateJson);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return field;
    }
    extra = parsed as Record<string, unknown>;
  } catch {
    return field;
  }
  const children = Array.isArray(extra.children)
    ? (extra.children as WhaleFormSubField[])
    : undefined;
  return {
    ...field,
    multiple: field.multiple ?? (typeof extra.multiple === 'boolean' ? extra.multiple : undefined),
    accept: field.accept ?? (typeof extra.accept === 'string' ? extra.accept : undefined),
    maxCount: field.maxCount ?? (typeof extra.maxCount === 'number' ? extra.maxCount : undefined),
    children: field.children ?? children,
    minRows: field.minRows ?? (typeof extra.minRows === 'number' ? extra.minRows : undefined),
    maxRows: field.maxRows ?? (typeof extra.maxRows === 'number' ? extra.maxRows : undefined),
  };
}

/** 外部值 → 内部值：switch 的 1/0 转 boolean；日期字符串转 dayjs；多选字符串转数组 */
function toInternalValues(
  fields: WhaleFormFieldConfig[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const internal: Record<string, unknown> = {};
  fields.forEach((field) => {
    const code = field.columnCode;
    if (!code || isLayoutWidget(field.widgetType)) {
      return;
    }
    const current = values[code];
    if (current === undefined || current === null) {
      // 显式置为 undefined，保证外部清空（重置）时内部值同步清空
      internal[code] = undefined;
    } else if (field.widgetType === 'switch') {
      internal[code] = current === 1 || current === '1' || current === true;
    } else if (
      (field.widgetType === 'date' || field.widgetType === 'datetime') &&
      typeof current === 'string'
    ) {
      internal[code] = dayjs(current);
    } else if (field.widgetType === 'checkbox' && typeof current === 'string') {
      internal[code] = current ? current.split(',') : [];
    } else if (field.widgetType === 'attachment') {
      // 文件 ID 统一按字符串承载，避免雪花 ID 经 Number() 丢精度
      internal[code] = Array.isArray(current) ? current.map((id) => String(id)) : current;
    } else {
      internal[code] = current;
    }
  });
  return internal;
}

/** 内部值 → 外部值：switch 的 boolean 转回 1/0 */
function toExternalValues(
  fields: WhaleFormFieldConfig[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const external: Record<string, unknown> = { ...values };
  fields.forEach((field) => {
    const code = field.columnCode;
    if (!code || field.widgetType !== 'switch') {
      return;
    }
    const current = external[code];
    if (current !== undefined && current !== null) {
      external[code] = current ? 1 : 0;
    }
  });
  return external;
}

/** 渲染纯布局字段（文本统一取 fieldLabel） */
function renderLayout(field: WhaleFormFieldConfig) {
  if (field.widgetType === 'divider') {
    return <Divider />;
  }
  if (field.widgetType === 'text-note') {
    return <Typography.Text type="secondary">{field.fieldLabel}</Typography.Text>;
  }
  return <Typography.Title level={5}>{field.fieldLabel}</Typography.Title>;
}

/** 构造鉴权请求头（令牌为空则不带头） */
function buildAuthHeaders(token?: string): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 默认上传：POST {apiBaseUrl}/file/upload（multipart 字段名 file） */
async function defaultUpload(file: File, runtime: FileRuntime): Promise<WhaleUploadedFile> {
  const body = new FormData();
  body.append('file', file);
  const response = await fetch(`${runtime.apiBaseUrl ?? ''}/file/upload`, {
    method: 'POST',
    headers: buildAuthHeaders(runtime.authToken),
    body,
  });
  if (!response.ok) {
    throw new Error(`上传失败（HTTP ${response.status}）`);
  }
  const result = (await response.json()) as {
    code?: number;
    message?: string;
    data?: { id?: string | number; originalName?: string; fileSize?: number };
  };
  if (result.code !== 0 || !result.data || result.data.id === undefined) {
    throw new Error(result.message || '上传失败');
  }
  return {
    id: String(result.data.id),
    name: result.data.originalName ?? file.name,
    size: typeof result.data.fileSize === 'number' ? result.data.fileSize : file.size,
  };
}

/** 默认下载：GET {apiBaseUrl}/file/{id}/download，按响应字节流触发浏览器保存 */
async function defaultDownload(file: WhaleUploadedFile, runtime: FileRuntime): Promise<void> {
  const response = await fetch(`${runtime.apiBaseUrl ?? ''}/file/${file.id}/download`, {
    headers: buildAuthHeaders(runtime.authToken),
  });
  if (!response.ok) {
    throw new Error(`下载失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.name ?? `file-${file.id}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** 文件大小展示（B / KB / MB） */
function formatSize(size?: number): string {
  if (size === undefined || size <= 0) {
    return '';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 附件值 → 文件 ID 字符串数组 */
function toFileIds(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => String(item)) : [];
}

/** 附件控件：上传 / 回显（文件名、大小）/ 下载 / 删除，值仅保存文件 ID 数组 */
function AttachmentControl({
  value,
  onChange,
  field,
  disabled,
  runtime,
}: {
  value?: unknown;
  onChange?: (next: string[]) => void;
  field: WhaleFormFieldConfig;
  disabled: boolean;
  runtime: FileRuntime;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [meta, setMeta] = useState<Record<string, WhaleUploadedFile>>({});
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ids = toFileIds(value);
  const maxCount = field.maxCount !== undefined && field.maxCount > 0 ? field.maxCount : 0;
  const limit = field.multiple === false ? 1 : maxCount;
  const limitReached = limit > 0 && ids.length >= limit;

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) {
      return;
    }
    const selected = Array.from(files);
    const allowed = limit > 0 ? selected.slice(0, Math.max(limit - ids.length, 0)) : selected;
    if (allowed.length < selected.length) {
      setError(`最多允许上传 ${limit} 个文件`);
    } else {
      setError(null);
    }
    if (allowed.length === 0) {
      return;
    }
    setUploading(true);
    try {
      const uploaded: WhaleUploadedFile[] = [];
      for (const file of allowed) {
        // 顺序上传，保持用户选择顺序
        uploaded.push(runtime.uploadFile ? await runtime.uploadFile(file) : await defaultUpload(file, runtime));
      }
      setMeta((previous) => {
        const next = { ...previous };
        uploaded.forEach((item) => {
          next[item.id] = item;
        });
        return next;
      });
      onChange?.([...ids, ...uploaded.map((item) => item.id)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (id: string) => {
    const file: WhaleUploadedFile = { id, name: meta[id]?.name };
    try {
      if (runtime.downloadFile) {
        await runtime.downloadFile(file);
        return;
      }
      await defaultDownload(file, runtime);
    } catch (e) {
      setError(e instanceof Error ? e.message : '下载失败');
    }
  };

  return (
    <div>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        {ids.length === 0 ? (
          <Typography.Text type="secondary">暂无附件</Typography.Text>
        ) : (
          ids.map((id) => (
            <Space key={id} size={8} wrap>
              <Typography.Text>{meta[id]?.name ?? `文件 #${id}`}</Typography.Text>
              {meta[id]?.size ? (
                <Typography.Text type="secondary">{formatSize(meta[id]?.size)}</Typography.Text>
              ) : null}
              <Button size="small" type="link" disabled={disabled} onClick={() => void handleDownload(id)}>
                下载
              </Button>
              <Button
                size="small"
                type="link"
                danger
                disabled={disabled}
                onClick={() => onChange?.(ids.filter((item) => item !== id))}
              >
                删除
              </Button>
            </Space>
          ))
        )}
        <Space wrap>
          <Button
            size="small"
            disabled={disabled || uploading || limitReached}
            loading={uploading}
            onClick={() => inputRef.current?.click()}
          >
            上传文件
          </Button>
          {limit > 0 ? <Typography.Text type="secondary">最多 {limit} 个</Typography.Text> : null}
          {field.accept ? (
            <Typography.Text type="secondary">支持类型：{field.accept}</Typography.Text>
          ) : null}
        </Space>
        {error ? <Typography.Text type="danger">{error}</Typography.Text> : null}
      </Space>
      <input
        ref={inputRef}
        type="file"
        accept={field.accept}
        multiple={field.multiple !== false}
        style={{ display: 'none' }}
        onChange={(event) => {
          const files = event.target.files;
          void handleFiles(files);
          event.target.value = '';
        }}
      />
    </div>
  );
}

/** 子表行值（键为子字段 key） */
type SubtableRow = Record<string, unknown>;

/** 子表值 → 行对象数组（非对象元素按空行处理） */
function toRows(value: unknown): SubtableRow[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((row) =>
    row !== null && typeof row === 'object' && !Array.isArray(row)
      ? { ...(row as SubtableRow) }
      : {},
  );
}

/** 子字段单元格控件（按基础类型渲染，值直接使用提交形态） */
function renderSubCell(
  child: WhaleFormSubField,
  cell: unknown,
  disabled: boolean,
  onChange: (next: unknown) => void,
) {
  switch (child.type) {
    case 'textarea':
      return (
        <Input.TextArea
          value={cell === null || cell === undefined ? '' : String(cell)}
          autoSize={{ minRows: 1, maxRows: 3 }}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        />
      );
    case 'number':
      return (
        <InputNumber
          style={{ width: '100%' }}
          value={typeof cell === 'number' ? cell : undefined}
          disabled={disabled}
          onChange={(next) => onChange(next ?? undefined)}
        />
      );
    case 'select':
      return (
        <Select
          allowClear
          style={{ width: '100%' }}
          value={cell === null || cell === undefined ? undefined : String(cell)}
          options={(child.options ?? []).map((option) => ({ label: option, value: option }))}
          disabled={disabled}
          onChange={(next) => onChange(next)}
        />
      );
    case 'date':
      return (
        <DatePicker
          style={{ width: '100%' }}
          format="YYYY-MM-DD"
          value={cell ? dayjs(String(cell)) : null}
          disabled={disabled}
          onChange={(next) => onChange(next ? next.format('YYYY-MM-DD') : undefined)}
        />
      );
    case 'datetime':
      return (
        <DatePicker
          showTime
          style={{ width: '100%' }}
          format="YYYY-MM-DD HH:mm:ss"
          value={cell ? dayjs(String(cell)) : null}
          disabled={disabled}
          onChange={(next) => onChange(next ? next.format('YYYY-MM-DD HH:mm:ss') : undefined)}
        />
      );
    case 'bool':
      return (
        <Switch
          size="small"
          checked={cell === true || cell === 1 || cell === '1'}
          disabled={disabled}
          onChange={(next) => onChange(next)}
        />
      );
    default:
      return (
        <Input
          value={cell === null || cell === undefined ? '' : String(cell)}
          maxLength={child.maxLength}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        />
      );
  }
}

/** 子表控件：行表格 + 增行/删行，单元格按子字段类型渲染 */
function SubtableControl({
  value,
  onChange,
  field,
  disabled,
}: {
  value?: unknown;
  onChange?: (next: SubtableRow[]) => void;
  field: WhaleFormFieldConfig;
  disabled: boolean;
}) {
  const rows = toRows(value);
  const children = field.children ?? [];
  const maxRows = field.maxRows !== undefined && field.maxRows > 0 ? field.maxRows : 0;
  const minRows = field.minRows !== undefined && field.minRows > 0 ? field.minRows : 0;

  if (children.length === 0) {
    return (
      <Typography.Text type="secondary">
        子表未配置子字段，请在设计器属性面板中添加
      </Typography.Text>
    );
  }

  const updateCell = (index: number, key: string, cell: unknown) => {
    onChange?.(rows.map((row, i) => (i === index ? { ...row, [key]: cell } : row)));
  };

  const columns: NonNullable<TableProps<SubtableRow>['columns']> = children.map((child) => ({
    title: (
      <span>
        {child.label || child.key}
        {child.required ? <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span> : null}
      </span>
    ),
    dataIndex: child.key,
    render: (_value: unknown, row: SubtableRow, index: number) =>
      renderSubCell(child, row[child.key], disabled, (cell) =>
        updateCell(index, child.key, cell),
      ),
  }));
  columns.push({
    title: '操作',
    key: '__action',
    width: 72,
    render: (_value: unknown, _row: SubtableRow, index: number) => (
      <Button
        size="small"
        type="link"
        danger
        disabled={disabled}
        onClick={() => onChange?.(rows.filter((_, i) => i !== index))}
      >
        删除
      </Button>
    ),
  });

  const hint = [minRows > 0 ? `至少 ${minRows} 行` : '', maxRows > 0 ? `最多 ${maxRows} 行` : '']
    .filter(Boolean)
    .join('，');

  return (
    <div>
      <Table<SubtableRow>
        size="small"
        bordered
        pagination={false}
        rowKey={(_row, index) => `row-${index ?? 0}`}
        columns={columns}
        dataSource={rows}
        locale={{ emptyText: '暂无明细' }}
        scroll={{ x: Math.max(children.length * 160, 400) }}
      />
      <Space style={{ marginTop: 8 }} wrap>
        <Button
          size="small"
          type="dashed"
          disabled={disabled || (maxRows > 0 && rows.length >= maxRows)}
          onClick={() => onChange?.([...rows, {}])}
        >
          新增行
        </Button>
        {hint ? <Typography.Text type="secondary">{hint}</Typography.Text> : null}
      </Space>
    </div>
  );
}

/** 渲染数据字段控件（disabled 已由调用方合并「整体禁用 + editable=0」） */
function renderControl(field: WhaleFormFieldConfig, disabled: boolean, runtime: FileRuntime) {
  switch (field.widgetType) {
    case 'textarea':
      return <Input.TextArea placeholder={field.placeholder} disabled={disabled} />;
    case 'number':
      return <InputNumber placeholder={field.placeholder} disabled={disabled} />;
    case 'select':
      return (
        <Select
          placeholder={field.placeholder}
          allowClear
          disabled={disabled}
          options={toOptions(field)}
        />
      );
    case 'radio':
      return <Radio.Group options={toOptions(field)} disabled={disabled} />;
    case 'checkbox':
      return <Checkbox.Group options={toOptions(field)} disabled={disabled} />;
    case 'date':
      return <DatePicker format="YYYY-MM-DD" placeholder={field.placeholder} disabled={disabled} />;
    case 'datetime':
      return (
        <DatePicker
          showTime
          format="YYYY-MM-DD HH:mm:ss"
          placeholder={field.placeholder}
          disabled={disabled}
        />
      );
    case 'switch':
      return <Switch disabled={disabled} />;
    case 'attachment':
      return <AttachmentControl field={field} disabled={disabled} runtime={runtime} />;
    case 'subtable':
      return <SubtableControl field={field} disabled={disabled} />;
    default:
      // text（含 widgetType 缺省）
      return (
        <Input
          maxLength={field.maxLength ?? 255}
          placeholder={field.placeholder}
          disabled={disabled}
        />
      );
  }
}

/** 受控动态表单渲染器：字段配置驱动渲染，值双向同步（switch 1/0 ↔ boolean、日期 ↔ dayjs、多选 ↔ 逗号串） */
export function FormRenderer({
  fields,
  value,
  onChange,
  disabled,
  authToken,
  apiBaseUrl,
  uploadFile,
  downloadFile,
}: FormRendererProps) {
  const [form] = Form.useForm();
  const runtime: FileRuntime = { authToken, apiBaseUrl, uploadFile, downloadFile };

  // 受控同步：外部 value 变化时写入内部 Form（含 switch 1/0 ↔ boolean 等类型转换）
  useEffect(() => {
    if (value === undefined) {
      return;
    }
    form.setFieldsValue(toInternalValues(fields, value));
  }, [fields, form, value]);

  // 内部值变化 → 回传全量字段值（switch 转回 1/0）
  const handleValuesChange = (
    _changedValues: Record<string, unknown>,
    allValues: Record<string, unknown>,
  ) => {
    onChange?.(toExternalValues(fields, allValues));
  };

  // 渲染单个字段：布局字段 / 数据字段（无 columnCode 的脏数据不渲染）
  const renderField = (field: WhaleFormFieldConfig) => {
    if (isLayoutWidget(field.widgetType)) {
      return renderLayout(field);
    }
    const columnCode = field.columnCode;
    if (!columnCode) {
      return null;
    }
    const resolved = resolveFieldExtras(field);
    const multiValue = field.widgetType === 'attachment' || field.widgetType === 'subtable';
    return (
      <Form.Item
        name={columnCode}
        label={field.fieldLabel}
        rules={
          field.required === 1
            ? [
                multiValue
                  ? {
                      required: true,
                      type: 'array' as const,
                      message: `请填写${field.fieldLabel}`,
                    }
                  : { required: true, message: `请输入${field.fieldLabel}` },
              ]
            : undefined
        }
        valuePropName={field.widgetType === 'switch' ? 'checked' : 'value'}
      >
        {renderControl(resolved, !!disabled || field.editable === 0, runtime)}
      </Form.Item>
    );
  };

  // 仅渲染表单中可见字段（showInForm !== 0，undefined 视为显示）
  const visibleFields = fields.filter((field) => field.showInForm !== 0);

  return (
    <Form form={form} layout="vertical" onValuesChange={handleValuesChange}>
      <Row gutter={16}>
        {visibleFields.map((field, index) => (
          <Col
            key={field.id ?? `${field.columnCode ?? 'layout'}-${index}`}
            span={field.colSpan ?? 24}
          >
            {renderField(field)}
          </Col>
        ))}
      </Row>
    </Form>
  );
}
