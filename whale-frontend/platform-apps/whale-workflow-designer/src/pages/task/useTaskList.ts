// 待办列表数据：受控分页 + 状态过滤，并补查当前页所属实例摘要（失败静默降级为 '-'）。

import { App } from 'antd';
import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { workflowApi, type Id, type ProcessTask } from '../../api/workflow';
import type { InstanceBrief, QueryState } from './constants';

export interface TaskListController {
  query: QueryState;
  setQuery: Dispatch<SetStateAction<QueryState>>;
  dataSource: ProcessTask[];
  total: number;
  loading: boolean;
  /** instanceId -> 所属实例摘要（列表展示与抽屉共用，避免重复补查） */
  instanceMap: Record<Id, InstanceBrief>;
  loadList: () => Promise<void>;
  /** 补写单条实例摘要（审批抽屉加载实例详情时顺带回填） */
  setInstanceBrief: (instanceId: Id, brief: InstanceBrief) => void;
}

export function useTaskList(): TaskListController {
  const { message } = App.useApp();
  const [query, setQuery] = useState<QueryState>({ current: 1, size: 10, status: 0 });
  const [dataSource, setDataSource] = useState<ProcessTask[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [instanceMap, setInstanceMap] = useState<Record<Id, InstanceBrief>>({});

  const setInstanceBrief = useCallback((instanceId: Id, brief: InstanceBrief) => {
    setInstanceMap((prev) => ({ ...prev, [instanceId]: brief }));
  }, []);

  /** 补查当前页待办所属实例摘要（单条失败静默降级为 '-'，不影响列表与审批） */
  const loadInstanceBriefs = useCallback(async (records: ProcessTask[]) => {
    const ids = Array.from(new Set(records.map((record) => record.instanceId)));
    const briefs = await Promise.all(
      ids.map(async (id) => {
        try {
          const instance = await workflowApi.instanceDetail(id);
          return [id, { definitionCode: instance.definitionCode, title: instance.title }] as const;
        } catch {
          return null;
        }
      }),
    );
    setInstanceMap((prev) => {
      const next = { ...prev };
      briefs.forEach((entry) => {
        if (entry) {
          next[entry[0]] = entry[1];
        }
      });
      return next;
    });
  }, []);

  /** 拉取待办分页列表（受控分页 + 状态过滤） */
  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await workflowApi.taskPage({
        current: query.current,
        size: query.size,
        status: query.status,
      });
      const records = result.records ?? [];
      setDataSource(records);
      setTotal(Number(result.total ?? 0));
      await loadInstanceBriefs(records);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, message, loadInstanceBriefs]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  return {
    query,
    setQuery,
    dataSource,
    total,
    loading,
    instanceMap,
    loadList,
    setInstanceBrief,
  };
}
