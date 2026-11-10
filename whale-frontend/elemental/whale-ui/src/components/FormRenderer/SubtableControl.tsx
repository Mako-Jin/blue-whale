// FormRenderer 子表控件：行表格 + 增行/删行，单元格按子字段基础类型渲染

import {
  Button,
  DatePicker,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import type { WhaleFormFieldConfig, WhaleFormSubField } from './types';

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
export function SubtableControl({
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
      <Typography.Text type="secondary">子表未配置子字段，请在设计器属性面板中添加</Typography.Text>
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
      renderSubCell(child, row[child.key], disabled, (cell) => updateCell(index, child.key, cell)),
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
