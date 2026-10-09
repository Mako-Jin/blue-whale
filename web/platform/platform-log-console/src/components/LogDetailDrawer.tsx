import type { ReactNode } from 'react';

import type { ClientLogVO } from 'common-api';

import { formatDateTime, toPrettyJson } from '../format';
import { LevelTag } from './LevelTag';

interface LogDetailDrawerProps {
  log: ClientLogVO | null;
  trace: { traceId: string; records: readonly ClientLogVO[] } | null;
  traceLoading: boolean;
  onClose: () => void;
  onLoadTrace: (traceId: string) => void;
  onClearTrace: () => void;
  onSelect: (log: ClientLogVO) => void;
}

export function LogDetailDrawer({
  log,
  trace,
  traceLoading,
  onClose,
  onLoadTrace,
  onClearTrace,
  onSelect,
}: LogDetailDrawerProps) {
  if (!log) return null;

  return (
    <aside className="drawer" role="dialog" aria-label="日志详情">
      <header className="drawer__head">
        <h3 className="drawer__title">日志详情</h3>
        <button type="button" className="btn" onClick={onClose}>
          关闭
        </button>
      </header>

      <div className="drawer__body">
        <dl className="kv">
          <Key label="级别">
            <LevelTag level={log.level} />
          </Key>
          <Key label="应用">{log.app ?? '—'}</Key>
          <Key label="命名空间">{log.namespace ?? '—'}</Key>
          <Key label="服务端时间">
            <span className="mono">{formatDateTime(log.serverTime)}</span>
          </Key>
          <Key label="客户端时间">
            <span className="mono">{formatDateTime(log.timestamp)}</span>
            <span className="kv__note">客户端时钟不可信，仅作参考</span>
          </Key>
          <Key label="路由">{log.route ?? '—'}</Key>
          <Key label="会话">{log.sessionId ?? '—'}</Key>
          <Key label="版本">{log.release ?? '—'}</Key>
          <Key label="来源 IP">{log.ip ?? '—'}</Key>
        </dl>

        <section className="drawer__section">
          <h4 className="drawer__subtitle">消息</h4>
          <pre className="code">{log.message ?? ''}</pre>
        </section>

        {log.data && log.data.length > 0 ? (
          <section className="drawer__section">
            <h4 className="drawer__subtitle">数据（data）</h4>
            <pre className="code">{toPrettyJson(log.data)}</pre>
          </section>
        ) : null}

        {log.context && Object.keys(log.context).length > 0 ? (
          <section className="drawer__section">
            <h4 className="drawer__subtitle">上下文（context）</h4>
            <pre className="code">{toPrettyJson(log.context)}</pre>
          </section>
        ) : null}

        <section className="drawer__section">
          <h4 className="drawer__subtitle">
            链路
            {log.traceId ? (
              <code className="mono drawer__trace">{log.traceId}</code>
            ) : (
              <span className="muted">该条日志没有链路标识</span>
            )}
          </h4>

          {log.traceId ? (
            trace ? (
              <>
                <div className="drawer__actions">
                  <span className="muted">共 {trace.records.length} 条，按发生顺序排列</span>
                  <button type="button" className="btn" onClick={onClearTrace}>
                    收起
                  </button>
                </div>
                <ol className="trace-list">
                  {trace.records.map((item) => (
                    <li
                      key={item.id ?? `${item.serverTime}-${item.message}`}
                      className={`trace-list__item${item.id === log.id ? ' trace-list__item--current' : ''}`}
                    >
                      <button
                        type="button"
                        className="trace-list__btn"
                        onClick={() => onSelect(item)}
                      >
                        <span className="mono trace-list__time">
                          {formatDateTime(item.serverTime)}
                        </span>
                        <LevelTag level={item.level} />
                        <span className="trace-list__msg">{item.message}</span>
                      </button>
                    </li>
                  ))}
                </ol>
              </>
            ) : (
              <button
                type="button"
                className="btn btn--primary"
                disabled={traceLoading}
                onClick={() => onLoadTrace(log.traceId as string)}
              >
                {traceLoading ? '加载中…' : '查看整条链路'}
              </button>
            )
          ) : null}
        </section>
      </div>
    </aside>
  );
}

function Key({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="kv__row">
      <dt className="kv__key">{label}</dt>
      <dd className="kv__value">{children}</dd>
    </div>
  );
}
