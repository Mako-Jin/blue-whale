import type { ClientEventPage, ClientEventStats, ClientEventVO } from 'common-api';

export interface ServerEventPanelProps {
  stats: ClientEventStats | null;
  page: ClientEventPage | null;
  loading: boolean;
  error: string | undefined;
  eventNameFilter: string;
  onEventNameFilterChange: (value: string) => void;
  onPageChange: (page: number) => void;
  onRefresh: () => void;
}

function pad(value: number, width = 2): string {
  return String(value).padStart(width, '0');
}

function formatTime(ms: number | undefined): string {
  if (typeof ms !== 'number') return '—';
  const date = new Date(ms);
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function summarizeProps(event: ClientEventVO): string {
  if (!event.props) return '—';
  const entries = Object.entries(event.props);
  if (entries.length === 0) return '{}';
  return JSON.stringify(Object.fromEntries(entries));
}

export function ServerEventPanel(props: ServerEventPanelProps) {
  const { stats, page } = props;
  const names = stats?.names ?? [];
  const max = names.reduce((acc, item) => Math.max(acc, item.eventNameCount ?? 0), 0);
  const totalPages = page?.pages ?? 0;

  return (
    <section className="panel panel--server">
      <header className="panel__head">
        <h2 className="panel__title">服务端视角</h2>
        <p className="panel__hint">按 serverTime 倒序读，客户端时间只作参照</p>
      </header>

      {/* -------------------------------------------------------- 聚合分布 */}
      <div className="block">
        <h3 className="block__title">
          按事件名聚合
          <span className="block__count">共 {stats?.total ?? 0} 条</span>
          <button type="button" className="btn btn--sm" onClick={props.onRefresh}>
            刷新
          </button>
        </h3>
        {names.length === 0 ? (
          <p className="block__note">
            最近 7 天没有事件。可能是后端未启动，或事件还停在客户端缓冲里。
          </p>
        ) : (
          <ul className="bars">
            {names.map((item) => (
              <li key={item.eventName ?? '(无名)'} className="bars__row">
                <span className="mono bars__label">{item.eventName}</span>
                <span className="bars__track">
                  <span
                    className="bars__fill"
                    style={{ width: `${max > 0 ? ((item.eventNameCount ?? 0) / max) * 100 : 0}%` }}
                  />
                </span>
                <span className="mono bars__value">{item.eventNameCount ?? 0}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* ---------------------------------------------------------- 过滤 */}
      <div className="block">
        <h3 className="block__title">查询</h3>
        <div className="row">
          <input
            className="input mono"
            placeholder="按事件名精确过滤，如 order.submit"
            value={props.eventNameFilter}
            onChange={(event) => props.onEventNameFilterChange(event.target.value)}
            aria-label="事件名过滤"
          />
          {props.loading ? <span className="pill">加载中…</span> : null}
        </div>
      </div>

      {props.error ? <div className="alert">{props.error}</div> : null}

      {/* ---------------------------------------------------------- 明细 */}
      <div className="block block--grow">
        <div className="tablewrap">
          <table className="table">
            <thead>
              <tr>
                <th>接收时间</th>
                <th>事件名</th>
                <th>用户</th>
                <th>设备</th>
                <th>会话</th>
                <th>路由</th>
                <th>属性</th>
                <th>v</th>
              </tr>
            </thead>
            <tbody>
              {(page?.records ?? []).length === 0 ? (
                <tr>
                  <td colSpan={8} className="table__empty">
                    没有数据
                  </td>
                </tr>
              ) : (
                (page?.records ?? []).map((event) => (
                  <tr key={event.id ?? event.eventId}>
                    <td className="mono">{formatTime(event.serverTime)}</td>
                    <td className="mono">{event.eventName}</td>
                    <td className="mono">{event.userId ?? '—'}</td>
                    <td className="mono" title={event.deviceId ?? ''}>
                      {event.deviceId ?? '—'}
                    </td>
                    <td className="mono" title={event.sessionId ?? ''}>
                      {event.sessionId ?? '—'}
                    </td>
                    <td className="mono">{event.route ?? '—'}</td>
                    <td className="mono cell--props" title={summarizeProps(event)}>
                      {summarizeProps(event)}
                    </td>
                    <td className="mono">{event.propsVersion ?? '—'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="pager">
          <button
            type="button"
            className="btn btn--sm"
            onClick={() => props.onPageChange(Math.max(1, (page?.page ?? 1) - 1))}
            disabled={(page?.page ?? 1) <= 1}
          >
            上一页
          </button>
          <span className="pager__info mono">
            {page?.page ?? 1} / {Math.max(1, totalPages)} · 共 {page?.total ?? 0} 条
          </span>
          <button
            type="button"
            className="btn btn--sm"
            onClick={() => props.onPageChange((page?.page ?? 1) + 1)}
            disabled={(page?.page ?? 1) >= totalPages}
          >
            下一页
          </button>
        </div>
      </div>
    </section>
  );
}
