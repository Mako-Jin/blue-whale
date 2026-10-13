import { useCallback, useEffect, useState } from 'react';
import type {
  AuditLogPage,
  AuditLogQuery,
  AuditResult,
  AuditStats,
  AuditStatsQuery,
} from 'common-api';

import { api } from '../api';
import { describeError } from '../errors';
import { DEFAULT_WINDOW, PAGE_SIZE, windowMillis } from './constants';
import type { TimeWindowKey } from './constants';

/** 界面上的筛选条件。全部是字符串，因为它们的直接来源是 `<input>` / `<select>` */
export type AuditFilters = {
  action: string;
  resource: string;
  targetId: string;
  operatorName: string;
  result: '' | AuditResult;
  window: TimeWindowKey;
};

const DEFAULT_FILTERS: AuditFilters = {
  action: '',
  resource: '',
  targetId: '',
  operatorName: '',
  result: '',
  window: DEFAULT_WINDOW,
};

export type AuditQueries = {
  filters: AuditFilters;
  /** 改筛选条件：回到第 1 页（换了条件还停在第 5 页，看到的是"空列表 + 有数据"的假象） */
  patchFilters(patch: Partial<AuditFilters>): void;
  resetFilters(): void;
  pageNo: number;
  setPageNo(page: number): void;
  page: AuditLogPage | null;
  stats: AuditStats | null;
  loading: boolean;
  error: string | null;
  /** 重新拉取（筛选条件与页码都不变） */
  reload(): void;
  /** 时间窗的实际起止，界面上要显示出来 —— 否则"只有 3 条"分不清是没数据还是窗口太小。
   * 首次加载完成前为 `null`（`Date.now()` 不能在渲染期调用，见下方 effect 里的说明） */
  window: { startTime: number; endTime: number } | null;
};

/**
 * 流水与聚合的数据加载。
 *
 * <h3>为什么两个查询写在一个 hook 里</h3>
 *
 * 它们共享**同一个时间窗**，而时间窗是这一页最重要的上下文：列表说"这段时间里有 7 条"，
 * 聚合说"其中 2 条失败"，两句话必须指同一段时间。分成两个 hook 各自持有一份时间窗，
 * 就会出现"切了窗口但只有一半刷新"的错位 —— 而且界面上看不出来。
 *
 * <h3>与权限管理台的数据加载有什么不同</h3>
 *
 * 那一边每个页面自己发请求（用户页 / 租户页互不相干）；这一边是**一页两视图**，
 * 因此把加载集中到一处，让"刷新"只有一个入口。
 */
export function useAuditQueries(enabled: boolean): AuditQueries {
  const [filters, setFilters] = useState<AuditFilters>(DEFAULT_FILTERS);
  const [pageNo, setPageNo] = useState(1);
  const [tick, setTick] = useState(0);
  const [page, setPage] = useState<AuditLogPage | null>(null);
  const [stats, setStats] = useState<AuditStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [window, setWindow] = useState<{ startTime: number; endTime: number } | null>(null);

  const reload = useCallback(() => setTick((value) => value + 1), []);

  const patchFilters = useCallback((patch: Partial<AuditFilters>) => {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPageNo(1);
  }, []);

  const resetFilters = useCallback(() => {
    setFilters(DEFAULT_FILTERS);
    setPageNo(1);
  }, []);

  /*
   * 权限未就绪时不发请求。
   *
   * 不是为了省一次请求：独立运行时"会话还没建立"就等于没有令牌，
   * 这时候发出去必然失败，而失败会触发一次刷新 —— 本该由启动流程做的事
   * 被一个页面 effect 抢了先。等 `ready` 再拉，时序就只有一个来源。
   *
   * 时间窗的右端必须锚在"现在"，而 `Date.now()` 不能在渲染期调用
   * （渲染要保持纯粹，否则 StrictMode 下的重复渲染会算出两个不同的窗口）。
   * 因此它在 effect 里算，算完连同两个查询一起交给界面显示。
   */
  useEffect(() => {
    if (!enabled) return undefined;

    let alive = true;

    const load = async (): Promise<void> => {
      setLoading(true);
      setError(null);

      const endTime = Date.now();
      const startTime = endTime - windowMillis(filters.window);
      setWindow({ startTime, endTime });

      const query: AuditLogQuery = {
        action: filters.action.trim() || undefined,
        resource: filters.resource.trim() || undefined,
        targetId: filters.targetId.trim() || undefined,
        operatorName: filters.operatorName.trim() || undefined,
        result: filters.result || undefined,
        startTime,
        endTime,
        page: pageNo,
        size: PAGE_SIZE,
      };

      // 聚合刻意**不带 action**（它按动作分组，带了就只剩一条），
      // 但**带 resource** —— 资源是分组之外的正交维度，筛得起
      const statsQuery: AuditStatsQuery = {
        resource: filters.resource.trim() || undefined,
        startTime,
        endTime,
      };

      try {
        const [pageResult, statsResult] = await Promise.all([
          api.audit.page(query),
          api.audit.stats(statsQuery),
        ]);
        if (!alive) return;
        setPage(pageResult);
        setStats(statsResult);
      } catch (cause) {
        if (!alive) return;
        setPage(null);
        setStats(null);
        setError(describeError(cause));
      } finally {
        if (alive) setLoading(false);
      }
    };

    void load();

    return () => {
      alive = false;
    };
  }, [enabled, filters, pageNo, tick]);

  return {
    filters,
    patchFilters,
    resetFilters,
    pageNo,
    setPageNo,
    page,
    stats,
    loading,
    error,
    reload,
    window,
  };
}
