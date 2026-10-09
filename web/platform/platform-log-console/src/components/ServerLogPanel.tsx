import type { ClientLogPage, ClientLogStats, ClientLogVO } from 'common-api';

import { formatClock, formatRelative } from '../format';
import { LEVEL_ORDER, levelLabel } from '../levels';
import { LevelTag } from './LevelTag';
import { StatsBar } from './StatsBar';

export interface LogFilters {
  level?: string;
  namespace?: string;
  keyword?: string;
}

interface ServerLogPanelProps {
  stats: ClientLogStats | null;
  page: ClientLogPage | null;
  loading: boolean;
  error: string | undefined;
  filters: LogFilters;
  onFiltersChange: (next: LogFilters) => void;
  onPageChange: (page: number) => void;
  onRefresh: () => void;
  onSelect: (log: ClientLogVO) => void;
  onViewTrace: (traceId: string) => void;
}

/**
 * 右侧：服务端日志。
 *
 * <p>这一栏的数据全部**来自后端**，与左栏的本地缓冲形成对照。
 * 判断"上报链路是否打通"的唯一可靠方式，就是看这里有没有出现左边刚产生的那条日志。
 */
export function ServerLogPanel({
  stats,
  page,
  loading,
  error,
  filters,
  onFiltersChange,
  onPageChange,
  onRefresh,
  onSelect,
  onViewTrace,
}: ServerLogPanelProps) {
  const records = page?.records ?? [];
  const total = page?.total ?? 0;
  const pages = page?.pages ?? 0;
  const currentPage = page?.page ?? 1;

  return (
    <section className="panel server">
      <header className="panel__head">
        <h2 className="panel__title">服务端日志</h2>
        <p className="panel__hint">
          数据来自后端接口，与左栏的本地缓冲互为对照 —— 左边产生、右边出现，才算链路打通
        </p>
      </header>

      {stats ? (
        <StatsBar
          levels={stats.levels ?? []}
          total={stats.total ?? 0}
          startTime={stats.startTime}
          endTime={stats.endTime}
          onPickLevel={(level) =>
            onFiltersChange({ ...filters, level: filters.level === level ? undefined : level })
          }
        />
      ) : null}

      <div className="filters">
        <select
          className="input"
          aria-label="级别"
          value={filters.level ?? ''}
          onChange={(event) =>
            onFiltersChange({ ...filters, level: event.target.value || undefined })
          }
        >
          <option value="">全部级别</option>
          {LEVEL_ORDER.map((level) => (
            <option key={level} value={level}>
              {levelLabel(level)}
            </option>
          ))}
        </select>

        <input
          className="input"
          aria-label="命名空间"
          placeholder="命名空间（精确匹配）"
          value={filters.namespace ?? ''}
          onChange={(event) =>
            onFiltersChange({ ...filters, namespace: event.target.value || undefined })
          }
        />

        <input
          className="input"
          aria-label="关键词"
          placeholder="消息关键词（模糊匹配）"
          value={filters.keyword ?? ''}
          onChange={(event) =>
            onFiltersChange({ ...filters, keyword: event.target.value || undefined })
          }
        />

        <button type="button" className="btn btn--primary" onClick={onRefresh}>
          刷新
        </button>
        <button
          type="button"
          className="btn"
          onClick={() =>
            onFiltersChange({ level: undefined, namespace: undefined, keyword: undefined })
          }
        >
          重置
        </button>
      </div>

      {error ? <div className="alert">{error}</div> : null}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th className="table__col-time">时间</th>
              <th className="table__col-level">级别</th>
              <th className="table__col-ns">命名空间</th>
              <th>消息</th>
              <th className="table__col-trace">链路</th>
            </tr>
          </thead>
          <tbody>
            {loading && records.length === 0 ? (
              <tr>
                <td colSpan={5} className="table__empty">
                  加载中…
                </td>
              </tr>
            ) : null}

            {!loading && records.length === 0 ? (
              <tr>
                <td colSpan={5} className="table__empty">
                  {total === 0
                    ? '没有查到日志。先在左边点几个按钮产生日志，等自动上报后按「刷新」。'
                    : '当前筛选条件下没有匹配的日志。'}
                </td>
              </tr>
            ) : null}

            {records.map((log) => (
              <tr key={log.id ?? `${log.serverTime}-${log.message}`} className="table__row">
                <td className="mono">
                  <button
                    type="button"
                    className="btn btn--link"
                    onClick={() => onSelect(log)}
                    title="查看详情"
                  >
                    {formatClock(log.serverTime)}
                  </button>
                  <span className="table__relative">{formatRelative(log.serverTime)}</span>
                </td>
                <td>
                  <LevelTag level={log.level} />
                </td>
                <td className="table__ns" title={log.namespace ?? ''}>
                  {log.namespace ?? '—'}
                </td>
                <td className="table__msg" title={log.message ?? ''}>
                  {log.message ?? '—'}
                </td>
                <td>
                  {log.traceId ? (
                    <button
                      type="button"
                      className="btn btn--link mono"
                      onClick={() => onViewTrace(log.traceId as string)}
                      title="查看整条链路"
                    >
                      {log.traceId.slice(0, 12)}…
                    </button>
                  ) : (
                    <span className="muted">—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <footer className="pager">
        <span className="pager__info">
          共 <strong className="mono">{total}</strong> 条，第 {currentPage} / {Math.max(pages, 1)}{' '}
          页
        </span>
        <div className="pager__actions">
          <button
            type="button"
            className="btn"
            disabled={currentPage <= 1}
            onClick={() => onPageChange(currentPage - 1)}
          >
            上一页
          </button>
          <button
            type="button"
            className="btn"
            disabled={pages === 0 || currentPage >= pages}
            onClick={() => onPageChange(currentPage + 1)}
          >
            下一页
          </button>
        </div>
      </footer>
    </section>
  );
}
