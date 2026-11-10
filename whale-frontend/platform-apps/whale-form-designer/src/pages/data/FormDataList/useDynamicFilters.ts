// 动态字段筛选状态（F8）：可筛选字段加载 + 条件草稿（字段/操作符/值）+ 条件增删。
// 从 FormDataList 拆出的状态 Hook：仅承载动态条件相关状态与操作，列表查询仍由容器组件负责。

import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { App } from 'antd';
import { formApi, type DataFilter, type FieldConfig, type FilterOp } from '../../../api/form';
import {
  FILTERABLE_TYPES,
  buildMultiValues,
  cleanDraftValues,
  filterOpsFor,
  needsValue,
  uniqueDraftValues,
} from './filterHelpers';

/** 动态条件状态与操作（容器组件与 FilterPanel 共享） */
export interface DynamicFiltersApi {
  /** 可筛选字段（按表单编码加载，已过滤不支持检索的列类型） */
  dynamicFields: FieldConfig[];
  /** 已添加的动态条件 */
  dynamicFilters: DataFilter[];
  /** 当前草稿选中的字段（值控件按其类型分派） */
  selectedField: FieldConfig | undefined;
  /** 草稿：字段编码 */
  draftField: string | undefined;
  /** 草稿：操作符 */
  draftOp: FilterOp;
  /** 草稿：标量值 */
  draftValue: string | number | undefined;
  /** 草稿：多值（in 集合 / between 区间；number 区间存数值，其余存文本） */
  draftValues: Array<string | number>;
  /** 当前字段可用操作符 */
  draftOps: FilterOp[];
  /** 选择过滤字段：联动切换可用操作符并清空值草稿 */
  selectField: (code: string) => void;
  /** 选择操作符：清空值草稿 */
  selectOp: (op: FilterOp) => void;
  /** 添加动态条件：按操作符与字段类型校验并组装 */
  addFilter: () => void;
  /** 移除动态条件 */
  removeFilter: (index: number) => void;
  /** 清空全部动态条件 */
  clearFilters: () => void;
  /** 清空草稿：字段/操作符/标量值/多值统一复位 */
  resetDraft: () => void;
  /** 设置标量值草稿 */
  setDraftValue: Dispatch<SetStateAction<string | number | undefined>>;
  /** 设置多值草稿 */
  setDraftValues: Dispatch<SetStateAction<Array<string | number>>>;
  /** 更新多值草稿指定下标（between 的起始/结束分别编辑） */
  updateDraftValueAt: (index: number, value: string | number) => void;
  /** 移除多值草稿指定下标（in 的日期集合 Tag 关闭） */
  removeDraftValueAt: (index: number) => void;
}

export function useDynamicFilters(definitionCode: string | undefined): DynamicFiltersApi {
  const { message } = App.useApp();

  const [dynamicFields, setDynamicFields] = useState<FieldConfig[]>([]);
  const [dynamicFilters, setDynamicFilters] = useState<DataFilter[]>([]);
  const [draftField, setDraftField] = useState<string | undefined>(undefined);
  const [draftOp, setDraftOp] = useState<FilterOp>('eq');
  const [draftValue, setDraftValue] = useState<string | number | undefined>(undefined);
  // in / between 的多值草稿（number 区间存数值，其余存文本）
  const [draftValues, setDraftValues] = useState<Array<string | number>>([]);

  const selectedField = useMemo(
    () => dynamicFields.find((field) => field.columnCode === draftField),
    [dynamicFields, draftField],
  );
  const draftOps = useMemo(() => filterOpsFor(selectedField?.dataType), [selectedField]);

  // 清空草稿：字段/操作符/标量值/多值统一复位
  const resetDraft = () => {
    setDraftField(undefined);
    setDraftOp('eq');
    setDraftValue(undefined);
    setDraftValues([]);
  };

  // 更新多值草稿指定下标（between 的起始/结束分别编辑）
  const updateDraftValueAt = (index: number, value: string | number) => {
    setDraftValues((prev) => {
      const next = [...prev];
      next[index] = value;
      return next;
    });
  };

  // 移除多值草稿指定下标（in 的日期集合 Tag 关闭）
  const removeDraftValueAt = (index: number) => {
    setDraftValues((prev) => prev.filter((_, current) => current !== index));
  };

  // 选择过滤字段：切换可用操作符为该字段类型的第一项，并清空值草稿
  const selectField = (value: string) => {
    const nextField = dynamicFields.find((field) => field.columnCode === value);
    setDraftField(value);
    setDraftOp(filterOpsFor(nextField?.dataType)[0]);
    setDraftValue(undefined);
    setDraftValues([]);
  };

  // 选择操作符：清空值草稿（旧值可能不适用于新操作符）
  const selectOp = (value: FilterOp) => {
    setDraftOp(value);
    setDraftValue(undefined);
    setDraftValues([]);
  };

  // 添加动态条件：标量按类型转换，in/between 组装数组，为空类操作符不携带值
  const addFilter = () => {
    if (!draftField) {
      message.warning('请选择过滤字段');
      return;
    }
    if (!needsValue(draftOp)) {
      setDynamicFilters((prev) => [...prev, { field: draftField, op: draftOp }]);
      resetDraft();
      return;
    }
    if (draftOp === 'in') {
      const cleaned = uniqueDraftValues(cleanDraftValues(draftValues));
      if (cleaned.length === 0) {
        message.warning('请填写至少一个过滤值');
        return;
      }
      const values = buildMultiValues(selectedField?.dataType, cleaned);
      if (!values) {
        message.warning('过滤值必须为数值');
        return;
      }
      setDynamicFilters((prev) => [...prev, { field: draftField, op: 'in', value: values }]);
      resetDraft();
      return;
    }
    if (draftOp === 'between') {
      const cleaned = cleanDraftValues(draftValues);
      if (cleaned.length < 2 || cleaned[0] === '' || cleaned[1] === '') {
        message.warning('介于需要填写起始和结束两个值');
        return;
      }
      const values = buildMultiValues(selectedField?.dataType, [cleaned[0], cleaned[1]]);
      if (!values) {
        message.warning('区间值必须为数值');
        return;
      }
      setDynamicFilters((prev) => [...prev, { field: draftField, op: 'between', value: values }]);
      resetDraft();
      return;
    }
    if (draftValue === undefined || draftValue === '') {
      message.warning('请填写过滤值');
      return;
    }
    const value = selectedField?.dataType === 'bool' ? draftValue === 'true' : draftValue;
    setDynamicFilters((prev) => [...prev, { field: draftField, op: draftOp, value }]);
    resetDraft();
  };

  // 移除动态条件
  const removeFilter = (index: number) => {
    setDynamicFilters((prev) => prev.filter((_, current) => current !== index));
  };

  // 清空全部动态条件（表单编码变更 / 重置时由容器调用）
  const clearFilters = () => {
    setDynamicFilters([]);
  };

  // 按表单编码加载可筛选字段：取该编码下最新版本定义，过滤掉不支持检索的列类型
  useEffect(() => {
    const code = definitionCode;
    if (!code) {
      setDynamicFields([]);
      return undefined;
    }
    let cancelled = false;
    void (async () => {
      try {
        const page = await formApi.page({ code, current: 1, size: 1 });
        const definition = page.records?.[0];
        if (!definition?.id) {
          if (!cancelled) {
            setDynamicFields([]);
          }
          return;
        }
        const detail = await formApi.detail(definition.id);
        if (!cancelled) {
          setDynamicFields(
            (detail.fields ?? []).filter(
              (field) => field.columnCode && field.dataType && FILTERABLE_TYPES.has(field.dataType),
            ),
          );
        }
      } catch {
        if (!cancelled) {
          setDynamicFields([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [definitionCode]);

  return {
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
    clearFilters,
    resetDraft,
    setDraftValue,
    setDraftValues,
    updateDraftValueAt,
    removeDraftValueAt,
  };
}
