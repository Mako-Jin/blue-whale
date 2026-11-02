// 动态条件筛选面板（F8）：过滤字段/操作符/值控件 + 已添加条件标签。
// 从 FormDataList 拆出的视图组件：只负责展示与交互，条件校验与状态流转见 useDynamicFilters。

import { App, Button, DatePicker, Input, InputNumber, Select, Space, Tag } from 'antd';
import type { Dayjs } from 'dayjs';
import {
  FILTER_OP_LABELS,
  MAX_IN_VALUES,
  fieldLabelOf,
  formatFilterValue,
  isMultiValueOp,
  needsValue,
  toDayjs,
} from './filterHelpers';
import type { DynamicFiltersApi } from './useDynamicFilters';

interface FilterPanelProps {
  /** 动态条件状态与操作（由 useDynamicFilters 提供） */
  filters: DynamicFiltersApi;
}

export default function FilterPanel({ filters }: FilterPanelProps) {
  const { message } = App.useApp();
  const {
    dynamicFields,
    dynamicFilters,
    selectedField,
    draftField,
    draftOp,
    draftValue,
    draftValues,
    draftOps,
    selectField,
    selectOp,
    addFilter,
    removeFilter,
    setDraftValue,
    setDraftValues,
    updateDraftValueAt,
    removeDraftValueAt,
  } = filters;

  // in 的日期/时间集合：已选值以 Tag 展示，DatePicker 选完即追加（受控保持空白）
  const renderInDateControl = () => {
    const isDateTime = selectedField?.dataType === 'datetime';
    const format = isDateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD';
    const handlePick = (value: Dayjs | null) => {
      if (!value) {
        return;
      }
      const text = value.format(format);
      if (draftValues.includes(text)) {
        return;
      }
      if (draftValues.length >= MAX_IN_VALUES) {
        message.warning(`最多添加 ${MAX_IN_VALUES} 个值`);
        return;
      }
      setDraftValues((prev) => [...prev, text]);
    };
    return (
      <Space wrap>
        {draftValues.map((value, index) => (
          <Tag key={`${String(value)}-${index}`} closable onClose={() => removeDraftValueAt(index)}>
            {String(value)}
          </Tag>
        ))}
        <DatePicker
          id="filter-in-date"
          showTime={isDateTime}
          format={format}
          placeholder={isDateTime ? '添加时间' : '添加日期'}
          style={{ width: 200 }}
          value={undefined}
          onChange={handlePick}
        />
      </Space>
    );
  };

  // in 条件值控件：日期/时间用「Tag + DatePicker」集合，布尔/选项字段走多选，其余用标签输入
  const renderInControl = () => {
    const dataType = selectedField?.dataType;
    if (dataType === 'date' || dataType === 'datetime') {
      return renderInDateControl();
    }
    if (dataType === 'bool') {
      return (
        <Select
          id="filter-value-multi"
          mode="multiple"
          placeholder="值（可多选）"
          style={{ minWidth: 140 }}
          value={draftValues as string[]}
          options={[
            { value: 'true', label: '是' },
            { value: 'false', label: '否' },
          ]}
          onChange={(values: string[]) => setDraftValues(values)}
        />
      );
    }
    const options = selectedField?.options;
    if (dataType === 'select' && options && options.length > 0) {
      return (
        <Select
          id="filter-value-multi"
          mode="multiple"
          placeholder="值（可多选）"
          style={{ minWidth: 180 }}
          value={draftValues as string[]}
          options={options.map((option) => ({
            value: option.optionValue,
            label: option.optionLabel,
          }))}
          onChange={(values: string[]) => setDraftValues(values)}
        />
      );
    }
    return (
      <Select
        id="filter-value-multi"
        mode="tags"
        placeholder="输入后回车，可多个"
        style={{ minWidth: 220 }}
        value={draftValues as string[]}
        tokenSeparators={[',']}
        onChange={(values: string[]) => setDraftValues(values)}
      />
    );
  };

  // between 条件值控件：数值用两个数字框，日期/时间用两个 DatePicker
  const renderBetweenControl = () => {
    if (selectedField?.dataType === 'number') {
      return (
        <Space.Compact>
          <InputNumber
            id="filter-between-from"
            placeholder="起始值"
            style={{ width: 120 }}
            value={typeof draftValues[0] === 'number' ? draftValues[0] : undefined}
            onChange={(value) => updateDraftValueAt(0, value ?? '')}
          />
          <InputNumber
            id="filter-between-to"
            placeholder="结束值"
            style={{ width: 120 }}
            value={typeof draftValues[1] === 'number' ? draftValues[1] : undefined}
            onChange={(value) => updateDraftValueAt(1, value ?? '')}
          />
        </Space.Compact>
      );
    }
    const isDateTime = selectedField?.dataType === 'datetime';
    const format = isDateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD';
    return (
      <Space.Compact>
        <DatePicker
          id="filter-between-from"
          showTime={isDateTime}
          format={format}
          placeholder={isDateTime ? '起始时间' : '起始日期'}
          style={{ width: 200 }}
          value={toDayjs(draftValues[0])}
          onChange={(value) => updateDraftValueAt(0, value ? value.format(format) : '')}
        />
        <DatePicker
          id="filter-between-to"
          showTime={isDateTime}
          format={format}
          placeholder={isDateTime ? '结束时间' : '结束日期'}
          style={{ width: 200 }}
          value={toDayjs(draftValues[1])}
          onChange={(value) => updateDraftValueAt(1, value ? value.format(format) : '')}
        />
      </Space.Compact>
    );
  };

  // 值控件分派：空值判断不渲染，in/between 走多值分支，其余按字段类型渲染标量
  const renderValueControl = () => {
    if (!needsValue(draftOp)) {
      return null;
    }
    if (isMultiValueOp(draftOp)) {
      return draftOp === 'in' ? renderInControl() : renderBetweenControl();
    }
    if (selectedField?.dataType === 'bool') {
      return (
        <Select
          id="filter-value-bool"
          placeholder="值"
          style={{ width: 110 }}
          value={draftValue as string | undefined}
          options={[
            { value: 'true', label: '是' },
            { value: 'false', label: '否' },
          ]}
          onChange={(value: string) => setDraftValue(value)}
        />
      );
    }
    if (selectedField?.dataType === 'number') {
      return (
        <InputNumber
          id="filter-value-number"
          placeholder="数值"
          style={{ width: 140 }}
          value={typeof draftValue === 'number' ? draftValue : undefined}
          onChange={(value) => setDraftValue(value ?? undefined)}
        />
      );
    }
    if (selectedField?.dataType === 'date' || selectedField?.dataType === 'datetime') {
      const isDateTime = selectedField.dataType === 'datetime';
      const format = isDateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD';
      return (
        <DatePicker
          id="filter-value-date"
          showTime={isDateTime}
          format={format}
          placeholder={isDateTime ? '时间' : '日期'}
          style={{ width: 200 }}
          value={toDayjs(draftValue)}
          onChange={(value) => setDraftValue(value ? value.format(format) : undefined)}
        />
      );
    }
    return (
      <Input
        id="filter-value-text"
        allowClear
        placeholder="过滤值"
        style={{ width: 170 }}
        value={typeof draftValue === 'string' ? draftValue : undefined}
        onChange={(event) => setDraftValue(event.target.value)}
      />
    );
  };

  return (
    <div style={{ marginTop: 12 }}>
      <Space wrap>
        <Select
          id="filter-field"
          placeholder="过滤字段"
          style={{ width: 170 }}
          value={draftField}
          options={dynamicFields.map((field) => ({
            value: field.columnCode as string,
            label: field.fieldLabel || field.columnCode || '',
          }))}
          onChange={selectField}
        />
        <Select
          id="filter-op"
          style={{ width: 130 }}
          value={draftOp}
          options={draftOps.map((op) => ({ value: op, label: FILTER_OP_LABELS[op] }))}
          onChange={selectOp}
        />
        {renderValueControl()}
        <Button onClick={addFilter}>添加条件</Button>
      </Space>
      {dynamicFilters.length > 0 ? (
        <div style={{ marginTop: 8 }}>
          {dynamicFilters.map((filter, index) => (
            <Tag
              key={`${filter.field}-${filter.op}-${index}`}
              closable
              onClose={() => removeFilter(index)}
            >
              {fieldLabelOf(dynamicFields, filter.field)} {FILTER_OP_LABELS[filter.op]}
              {needsValue(filter.op) ? ` ${formatFilterValue(filter)}` : ''}
            </Tag>
          ))}
        </div>
      ) : null}
    </div>
  );
}
