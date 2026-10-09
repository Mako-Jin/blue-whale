import type { LogLevel, LogRecord } from 'common-tools';

import { formatClock } from '../format';
import { LEVEL_ORDER, levelClassName, levelLabel } from '../levels';
import type { ReporterState } from '../reporter';
import { LevelTag } from './LevelTag';

interface GeneratorPanelProps {
  records: readonly LogRecord[];
  reporterState: ReporterState;
  autoReport: boolean;
  lastTraceId: string | undefined;
  onToggleAutoReport: (next: boolean) => void;
  onClear: () => void;
  onEmitLevel: (level: LogLevel) => void;
  onBurst: (count: number) => void;
  onSimulateChain: (kind: 'success' | 'failure') => void;
  onRaiseError: () => void;
  onViewTrace: (traceId: string) => void;
}

/**
 * 左侧：日志产生器 + 本地缓冲。
 *
 * <p>左栏展示的是**客户端视角**（我产生了什么、上报了多少），
 * 右栏展示的是**服务端视角**（后端实际收到了什么）。两者并排，
 * 才能一眼看出"上报到底有没有成功"—— 只看其中一边永远无法判断。
 */
export function GeneratorPanel({
  records,
  reporterState,
  autoReport,
  lastTraceId,
  onToggleAutoReport,
  onClear,
  onEmitLevel,
  onBurst,
  onSimulateChain,
  onRaiseError,
  onViewTrace,
}: GeneratorPanelProps) {
  return (
    <section className="panel generator">
      <header className="panel__head">
        <h2 className="panel__title">产生日志</h2>
        <p className="panel__hint">走真实的 logger，级别过滤与命名空间层级都会生效</p>
      </header>

      <div className="panel__body">
        <div className="field">
          <label className="field__label" htmlFor="auto-report">
            自动上报
          </label>
          <input
            id="auto-report"
            type="checkbox"
            className="switch"
            checked={autoReport}
            onChange={(event) => onToggleAutoReport(event.target.checked)}
          />
          <span className="field__note">
            {autoReport ? '攒够一批或到时即发' : '已暂停，日志只留在本地'}
          </span>
        </div>

        <div className="generator__group">
          <div className="generator__group-title">按级别产生</div>
          <div className="btn-row">
            {LEVEL_ORDER.map((level) => (
              <button
                key={level}
                type="button"
                className={`btn btn--level ${levelClassName(level)}`}
                onClick={() => onEmitLevel(level)}
              >
                {levelLabel(level)}
              </button>
            ))}
          </div>
          <div className="btn-row">
            <button type="button" className="btn" onClick={() => onBurst(10)}>
              随机 10 条
            </button>
            <button type="button" className="btn" onClick={() => onBurst(60)}>
              随机 60 条
            </button>
          </div>
        </div>

        <div className="generator__group">
          <div className="generator__group-title">特殊场景</div>
          <div className="btn-row">
            <button type="button" className="btn" onClick={onRaiseError}>
              抛异常（带堆栈）
            </button>
            <button type="button" className="btn" onClick={() => onSimulateChain('success')}>
              模拟成功链路
            </button>
            <button type="button" className="btn" onClick={() => onSimulateChain('failure')}>
              模拟失败链路
            </button>
          </div>
          {lastTraceId ? (
            <div className="trace-hint">
              <span className="trace-hint__label">最近链路</span>
              <code className="mono trace-hint__id">{lastTraceId}</code>
              <button
                type="button"
                className="btn btn--link"
                onClick={() => onViewTrace(lastTraceId)}
              >
                查看整条链路
              </button>
            </div>
          ) : null}
        </div>

        <div className="generator__group generator__group--grow">
          <div className="generator__group-title">
            本地缓冲
            <span className="badge mono">{reporterState.pending}</span>
            <button type="button" className="btn btn--link" onClick={onClear}>
              清空
            </button>
          </div>

          <ul className="local-list">
            {records.length === 0 ? (
              <li className="local-list__empty">还没有日志，点上面的按钮产生几条</li>
            ) : (
              records.map((record, index) => (
                <li
                  key={`${record.timestamp}-${index}`}
                  className="local-list__item"
                  onClick={() => record.traceId && onViewTrace(record.traceId)}
                >
                  <span className="mono local-list__time">{formatClock(record.timestamp)}</span>
                  <LevelTag level={record.level} />
                  <span className="local-list__ns">{record.namespace}</span>
                  <span className="local-list__msg">{record.message}</span>
                </li>
              ))
            )}
          </ul>
        </div>
      </div>

      <footer className="generator__stats">
        <Stat label="待上报" value={reporterState.pending} />
        <Stat label="已落库" value={reporterState.sent} tone="ok" />
        <Stat
          label="被拒"
          value={reporterState.rejected}
          tone={reporterState.rejected ? 'warn' : undefined}
        />
        <Stat
          label="失败批次"
          value={reporterState.failedBatches}
          tone={reporterState.failedBatches ? 'err' : undefined}
        />
        <Stat
          label="丢弃"
          value={reporterState.dropped}
          tone={reporterState.dropped ? 'warn' : undefined}
        />
      </footer>

      {reporterState.lastError ? (
        <div className="generator__error">
          上报失败：{reporterState.lastError}
          {reporterState.backoffMs > 0
            ? `（已退避至 ${Math.round(reporterState.backoffMs / 1000)}s 后重试）`
            : null}
        </div>
      ) : null}
    </section>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: 'ok' | 'warn' | 'err';
}) {
  return (
    <span className={`stat${tone ? ` stat--${tone}` : ''}`}>
      <span className="stat__value mono">{value}</span>
      <span className="stat__label">{label}</span>
    </span>
  );
}
