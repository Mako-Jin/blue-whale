// 设计器数据加载（F6）：表单定义 / 所属表列 / 表定义下拉。
// 数据源：GET /form/definition/{id}（含关联列冗余回填）；列来源：GET /form/table-definition/{tid}。
// 从 FormDesigner 拆出的状态 Hook：仅承载加载相关状态与副作用，编辑与保存仍由容器组件负责。

import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { App } from 'antd';
import {
  formApi,
  tableDefinitionApi,
  type ColumnMeta,
  type FormDefinition,
  type Id,
} from '../../api/form';
import { hydrateField, nextUid, type DesignerField } from './types';

interface UseDesignerDataParams {
  /** 路由中的表单定义 ID；null 为新建 */
  definitionId: string | null;
  /** URL query 携带的表定义 ID（新建表单视图时由 FormList 传入） */
  queryTableId: string | null;
  /** 历史栈复位：服务端加载结果作为新历史基点（见 useFieldHistory） */
  resetFields: (initial: DesignerField[]) => void;
  /** 选中态复位：加载 / 切换表单后取消画布选中 */
  resetSelection: () => void;
}

/** 设计器数据状态（容器组件共享） */
export interface DesignerDataApi {
  /** 表单定义 / 表列加载中 */
  loading: boolean;
  /** 当前表单定义（新建为 null） */
  definition: FormDefinition | null;
  /** 顶栏元信息：编码 */
  code: string;
  setCode: Dispatch<SetStateAction<string>>;
  /** 顶栏元信息：名称 */
  name: string;
  setName: Dispatch<SetStateAction<string>>;
  /** 所属表定义 ID */
  tableId: Id | null;
  setTableId: Dispatch<SetStateAction<Id | null>>;
  /** 「所属表定义」下拉数据 */
  tableOptions: { value: Id; label: string }[];
  /** 表定义列（调色板数据字段来源） */
  columns: ColumnMeta[];
  /** 列元数据按 ID 索引（画布回填与属性面板只读展示用） */
  columnsById: Map<Id, ColumnMeta>;
}

export function useDesignerData({
  definitionId,
  queryTableId,
  resetFields,
  resetSelection,
}: UseDesignerDataParams): DesignerDataApi {
  const { message } = App.useApp();

  const [loading, setLoading] = useState(true);
  const [definition, setDefinition] = useState<FormDefinition | null>(null);

  // 顶栏元信息
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [tableId, setTableId] = useState<Id | null>(null);
  const [tableOptions, setTableOptions] = useState<{ value: Id; label: string }[]>([]);

  // 列来源
  const [columns, setColumns] = useState<ColumnMeta[]>([]);

  const columnsById = useMemo(
    () =>
      new Map(
        columns
          .filter((column) => column.id !== undefined)
          .map((column) => [column.id as Id, column]),
      ),
    [columns],
  );

  // 加载表单定义与画布（新建 / 编辑），并解析所属表定义
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const resolve = async () => {
      const queryTable = queryTableId;
      try {
        if (definitionId !== null) {
          const detail = await formApi.detail(definitionId);
          if (cancelled) {
            return;
          }
          setDefinition(detail);
          setCode(detail.code);
          setName(detail.name);
          setTableId(detail.tableDefinitionId ?? queryTable ?? null);
          // 服务端加载结果作为历史基点：不入历史并清空 past / future
          resetFields(
            (detail.fields ?? [])
              .slice()
              .sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
              .map((field) => ({ ...hydrateField(field), _uid: nextUid() })),
          );
        } else {
          setDefinition(null);
          setCode('');
          setName('');
          setTableId(queryTable ?? null);
          resetFields([]);
        }
        resetSelection();
      } catch (error) {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '表单定义加载失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void resolve();
    return () => {
      cancelled = true;
    };
  }, [definitionId, queryTableId, message, resetFields, resetSelection]);

  // 加载表定义列（作为调色板数据字段来源）
  useEffect(() => {
    if (tableId === null) {
      setColumns([]);
      return;
    }
    let cancelled = false;
    tableDefinitionApi
      .detail(tableId)
      .then((table) => {
        if (!cancelled) {
          setColumns(table.columns ?? []);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '表定义加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [tableId, message]);

  // 加载「所属表定义」下拉数据
  useEffect(() => {
    let cancelled = false;
    formApi
      .tables({ current: 1, size: 200 })
      .then((result) => {
        if (!cancelled) {
          setTableOptions(
            result.records
              .filter((table) => table.id !== undefined)
              .map((table) => ({
                value: table.id as Id,
                label: `${table.tableCode} - ${table.tableName}`,
              })),
          );
        }
      })
      .catch(() => {
        // 下拉数据失败不阻塞设计器主流程
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return {
    loading,
    definition,
    code,
    setCode,
    name,
    setName,
    tableId,
    setTableId,
    tableOptions,
    columns,
    columnsById,
  };
}
