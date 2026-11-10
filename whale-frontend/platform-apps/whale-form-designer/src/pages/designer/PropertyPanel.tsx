// 设计器右侧属性面板（F6）：编辑选中字段属性。
// 数据字段的「关联列 / 存储类型 / 必填 / 长度」来自列元数据（只读）；控件候选按存储类型过滤。
// 使用受控输入直连画布状态（不依赖 Form 实例），避免与画布受控数据冲突。

import {
  Button,
  Empty,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import type { ReactNode } from 'react';
import type {
  BasicDataType,
  ColumnMeta,
  FieldOption,
  Id,
  SubFieldConfig,
  WidgetType,
} from '../../api/form';
import {
  BASIC_DATA_TYPES,
  BASIC_TYPE_LABELS,
  WIDGET_LABELS,
  isLayoutField,
  widgetCandidates,
  type DesignerField,
} from './types';

/** 简单表单项：标签 + 控件 */
function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <div style={{ marginBottom: 4, color: '#666' }}>{label}</div>
      {children}
    </div>
  );
}

/** 选项编辑（下拉/单选/多选） */
function OptionEditor({
  options,
  onChange,
}: {
  options?: FieldOption[];
  onChange: (next: FieldOption[]) => void;
}) {
  const list = options ?? [];
  const update = (index: number, patch: Partial<FieldOption>) =>
    onChange(list.map((option, i) => (i === index ? { ...option, ...patch } : option)));
  return (
    <div>
      {list.map((option, index) => (
        <Space key={index} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
          <Input
            placeholder="选项名"
            value={option.optionLabel}
            onChange={(event) => update(index, { optionLabel: event.target.value })}
          />
          <Input
            placeholder="选项值"
            value={option.optionValue}
            onChange={(event) => update(index, { optionValue: event.target.value })}
          />
          <Button type="text" danger onClick={() => onChange(list.filter((_, i) => i !== index))}>
            删除
          </Button>
        </Space>
      ))}
      <Button
        type="dashed"
        block
        onClick={() =>
          onChange([...list, { optionLabel: '', optionValue: '', sortNo: list.length }])
        }
      >
        新增选项
      </Button>
    </div>
  );
}

/** 子表子字段编辑器：添加/删除、key/label/类型/必填 */
function SubFieldEditor({
  children,
  onChange,
}: {
  children?: SubFieldConfig[];
  onChange: (next: SubFieldConfig[]) => void;
}) {
  const list = children ?? [];
  const update = (index: number, patch: Partial<SubFieldConfig>) =>
    onChange(list.map((child, i) => (i === index ? { ...child, ...patch } : child)));
  return (
    <div>
      {list.length === 0 ? (
        <Typography.Text type="secondary">尚未添加子字段</Typography.Text>
      ) : null}
      {list.map((child, index) => (
        <div
          key={index}
          style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 8, marginBottom: 8 }}
        >
          <Space direction="vertical" size={6} style={{ width: '100%' }}>
            <Space wrap>
              <Input
                style={{ width: 150 }}
                placeholder="key（如 item_name）"
                value={child.key}
                onChange={(event) => update(index, { key: event.target.value })}
              />
              <Input
                style={{ width: 110 }}
                placeholder="显示名"
                value={child.label}
                onChange={(event) => update(index, { label: event.target.value })}
              />
            </Space>
            <Space wrap>
              <Select
                style={{ width: 110 }}
                value={child.type}
                options={BASIC_DATA_TYPES.map((type) => ({
                  value: type,
                  label: BASIC_TYPE_LABELS[type],
                }))}
                onChange={(value: BasicDataType) => update(index, { type: value })}
              />
              <Typography.Text type="secondary">必填</Typography.Text>
              <Switch
                size="small"
                checked={child.required === true}
                onChange={(checked) => update(index, { required: checked })}
              />
              <Button
                type="link"
                danger
                onClick={() => onChange(list.filter((_, i) => i !== index))}
              >
                删除
              </Button>
            </Space>
          </Space>
        </div>
      ))}
      <Button
        type="dashed"
        block
        onClick={() => onChange([...list, { key: '', label: '', type: 'text', required: false }])}
      >
        新增子字段
      </Button>
    </div>
  );
}

interface PropertyPanelProps {
  field: DesignerField | null;
  columnsById: Map<Id, ColumnMeta>;
  onChange: (patch: Partial<DesignerField>) => void;
}

export default function PropertyPanel({ field, columnsById, onChange }: PropertyPanelProps) {
  if (!field) {
    return <Empty description="请选择画布中的字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  const layout = isLayoutField(field);
  const column = field.columnMetaId != null ? columnsById.get(field.columnMetaId) : undefined;
  const dataType = column?.dataType ?? field.dataType;
  const columnCode = column?.columnCode ?? field.columnCode;
  const required = column?.required ?? field.required;
  const maxLength = column?.length ?? field.maxLength;

  const spanEditor = (
    <Radio.Group
      value={field.colSpan ?? 24}
      onChange={(event) => onChange({ colSpan: event.target.value })}
      optionType="button"
      buttonStyle="solid"
      options={[
        { label: '整宽 (24)', value: 24 },
        { label: '半宽 (12)', value: 12 },
      ]}
    />
  );

  // 布局字段：仅文本与栅格可编辑
  if (layout) {
    return (
      <div>
        <Item label="控件类型">
          <Tag>{field.widgetType ? WIDGET_LABELS[field.widgetType] : '布局'}</Tag>
        </Item>
        <Item
          label={
            field.widgetType === 'title'
              ? '标题文字'
              : field.widgetType === 'text-note'
                ? '说明文字'
                : '文本'
          }
        >
          <Input
            value={field.fieldLabel}
            onChange={(event) => onChange({ fieldLabel: event.target.value })}
          />
        </Item>
        <Item label="栅格宽度">{spanEditor}</Item>
        <Typography.Text type="secondary">
          纯布局字段不绑定表单值，也不参与数据提交。
        </Typography.Text>
      </div>
    );
  }

  const showOptions =
    field.widgetType === 'select' ||
    field.widgetType === 'radio' ||
    field.widgetType === 'checkbox';
  const isAttachment = dataType === 'attachment' || field.widgetType === 'attachment';
  const isSubtable = dataType === 'subtable' || field.widgetType === 'subtable';

  return (
    <div>
      <Item label="字段显示名">
        <Input
          value={field.fieldLabel}
          onChange={(event) => onChange({ fieldLabel: event.target.value })}
        />
      </Item>
      <Item label="关联列（只读）">
        <Space wrap>
          <Tag color="blue">{columnCode ?? '-'}</Tag>
          <Tag>{dataType ?? '-'}</Tag>
          {required === 1 ? <Tag color="red">必填</Tag> : <Tag>非必填</Tag>}
          {maxLength != null ? <Tag>长度 {maxLength}</Tag> : null}
        </Space>
      </Item>
      <Item label="控件类型">
        <Select
          style={{ width: '100%' }}
          value={field.widgetType}
          onChange={(value: WidgetType) => onChange({ widgetType: value })}
          options={widgetCandidates(dataType).map((widget) => ({
            value: widget,
            label: WIDGET_LABELS[widget],
          }))}
        />
      </Item>
      <Item label="栅格宽度">{spanEditor}</Item>
      <Item label="列表显示">
        <Switch
          checked={field.showInList !== 0}
          onChange={(checked) => onChange({ showInList: checked ? 1 : 0 })}
        />
      </Item>
      <Item label="表单显示">
        <Switch
          checked={field.showInForm !== 0}
          onChange={(checked) => onChange({ showInForm: checked ? 1 : 0 })}
        />
      </Item>
      <Item label="可编辑">
        <Switch
          checked={field.editable !== 0}
          onChange={(checked) => onChange({ editable: checked ? 1 : 0 })}
        />
      </Item>
      <Item label="占位提示">
        <Input
          value={field.placeholder}
          onChange={(event) => onChange({ placeholder: event.target.value })}
        />
      </Item>
      <Item label="默认值">
        <Input
          value={field.defaultValue}
          onChange={(event) => onChange({ defaultValue: event.target.value })}
        />
      </Item>
      {showOptions ? (
        <Item label="选项">
          <OptionEditor options={field.options} onChange={(next) => onChange({ options: next })} />
        </Item>
      ) : null}
      {isAttachment ? (
        <>
          <Item label="允许多文件">
            <Switch
              checked={field.multiple !== false}
              onChange={(checked) => onChange({ multiple: checked })}
            />
          </Item>
          <Item label="最大文件数（0 不限）">
            <InputNumber
              min={0}
              style={{ width: '100%' }}
              value={field.maxCount ?? 0}
              onChange={(value) => onChange({ maxCount: typeof value === 'number' ? value : 0 })}
            />
          </Item>
          <Item label="允许的文件类型">
            <Input
              value={field.accept}
              placeholder="如 image/*,.pdf"
              onChange={(event) => onChange({ accept: event.target.value })}
            />
          </Item>
        </>
      ) : null}
      {isSubtable ? (
        <>
          <Item label="子字段">
            <SubFieldEditor
              children={field.children}
              onChange={(next) => onChange({ children: next })}
            />
          </Item>
          <Item label="最少行数（0 不限）">
            <InputNumber
              min={0}
              style={{ width: '100%' }}
              value={field.minRows ?? 0}
              onChange={(value) => onChange({ minRows: typeof value === 'number' ? value : 0 })}
            />
          </Item>
          <Item label="最多行数（0 不限）">
            <InputNumber
              min={0}
              style={{ width: '100%' }}
              value={field.maxRows ?? 0}
              onChange={(value) => onChange({ maxRows: typeof value === 'number' ? value : 0 })}
            />
          </Item>
        </>
      ) : null}
    </div>
  );
}
