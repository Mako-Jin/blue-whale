import type { Health, ClientLogPage, ClientLogStats, ClientLogVO } from 'common-api';
import type { LogLevel } from 'common-tools';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import './App.less';
import { GeneratorPanel } from './components/GeneratorPanel';
import { LogDetailDrawer } from './components/LogDetailDrawer';
import type { LogFilters } from './components/ServerLogPanel';
import { ServerLogPanel } from './components/ServerLogPanel';
import {
  APP_ID,
  APP_RELEASE,
  SESSION_ID,
  api,
  clearLocalRecords,
  getLocalRecords,
  logSource,
  onLocalRecordsChange,
  onReporterChange,
  reporter,
  setCurrentTraceId,
} from './console';
import type { ReporterState } from './reporter';

const PAGE_SIZE = 20;

/** 后端接口文档地址（由 springdoc 提供，与后端同源） */
const SWAGGER_URL = '/swagger-ui.html';

export default function App() {
  const [reporterState, setReporterState] = useState<ReporterState>(() => reporter.getState());
  const [localTick, setLocalTick] = useState(0);
  const [autoReport, setAutoReport] = useState(true);

  const [health, setHealth] = useState<Health | null>(null);
  const [healthError, setHealthError] = useState<string | undefined>(undefined);

  const [stats, setStats] = useState<ClientLogStats | null>(null);
  const [page, setPage] = useState<ClientLogPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [filters, setFilters] = useState<LogFilters>({});
  const [loading, setLoading] = useState(false);
  const [queryError, setQueryError] = useState<string | undefined>(undefined);
  const [refreshTick, setRefreshTick] = useState(0);

  const [selected, setSelected] = useState<ClientLogVO | null>(null);
  const [trace, setTrace] = useState<{ traceId: string; records: readonly ClientLogVO[] } | null>(
    null,
  );
  const [traceLoading, setTraceLoading] = useState(false);
  const [lastTraceId, setLastTraceId] = useState<string | undefined>(undefined);

  /* ------------------------------------------------------------- 订阅 */

  useEffect(() => onReporterChange(setReporterState), []);
  useEffect(() => onLocalRecordsChange(() => setLocalTick((n) => n + 1)), []);

  /* ------------------------------------------- 上报开关 → 启动/停止缓冲 */

  useEffect(() => {
    if (autoReport) reporter.start();
    else reporter.stop();
    return () => reporter.stop();
  }, [autoReport]);

  /* --------------------------------------------------------- 健康检查 */

  useEffect(() => {
    let alive = true;

    const probe = async () => {
      try {
        const result = await api.system.health();
        if (alive) {
          setHealth(result);
          setHealthError(undefined);
        }
      } catch (error) {
        if (alive) {
          setHealth(null);
          setHealthError(error instanceof Error ? error.message : String(error));
        }
      }
    };

    void probe();
    // 10 秒一次：既是状态指示，也顺带证明"后端起着呢"
    const timer = setInterval(() => void probe(), 10_000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  /* ------------------------------------------------------- 服务端查询 */

  useEffect(() => {
    let alive = true;

    const load = async () => {
      setLoading(true);
      try {
        const [statsResult, pageResult] = await Promise.all([
          api.log.stats({}),
          api.log.page({
            level: filters.level,
            namespace: filters.namespace,
            keyword: filters.keyword,
            page: pageNo,
            size: PAGE_SIZE,
          }),
        ]);
        if (!alive) return;
        setStats(statsResult);
        setPage(pageResult);
        setQueryError(undefined);
      } catch (error) {
        if (!alive) return;
        setQueryError(
          error instanceof Error
            ? `查询失败：${error.message}（确认后端已启动：cd server && mvn -pl :sts-app-server spring-boot:run）`
            : String(error),
        );
      } finally {
        if (alive) setLoading(false);
      }
    };

    void load();
    return () => {
      alive = false;
    };
  }, [filters, pageNo, refreshTick]);

  /* --------------------------- 上报成功后自动刷新，让"左边产生右边出现"可见 */

  const lastSent = useRef(0);
  useEffect(() => {
    if (reporterState.sent > lastSent.current) {
      lastSent.current = reporterState.sent;
      setRefreshTick((n) => n + 1);
    }
  }, [reporterState.sent]);

  /* ----------------------------------------------------------- 交互 */

  const handleEmitLevel = useCallback((level: LogLevel) => {
    logSource.emit(level, 'app › manual', `手动产生的一条 ${level} 日志`, [{ from: 'console' }]);
  }, []);

  const handleBurst = useCallback((count: number) => {
    logSource.burst(count);
  }, []);

  const handleSimulateChain = useCallback((kind: 'success' | 'failure') => {
    const traceId =
      kind === 'success' ? logSource.simulateSuccessChain() : logSource.simulateFailureChain();
    setLastTraceId(traceId);
    setCurrentTraceId(traceId);
  }, []);

  const handleRaiseError = useCallback(() => {
    logSource.raiseError();
  }, []);

  const loadTrace = useCallback(async (traceId: string) => {
    setTraceLoading(true);
    try {
      const records = await api.log.listByTraceId(traceId);
      setTrace({ traceId, records });
    } catch (error) {
      setQueryError(error instanceof Error ? error.message : String(error));
    } finally {
      setTraceLoading(false);
    }
  }, []);

  const handleViewTrace = useCallback(
    (traceId: string) => {
      setCurrentTraceId(traceId);
      void loadTrace(traceId);
    },
    [loadTrace],
  );

  const records = useMemo(() => {
    void localTick;
    return getLocalRecords();
  }, [localTick]);

  const online = health?.status === 'UP';

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo" aria-hidden="true" />
          <div>
            <h1 className="topbar__title">BlueWhale 日志控制台</h1>
            <p className="topbar__subtitle">
              前端日志上报 → 服务端落库 → 查询与链路追踪的端到端演示
            </p>
          </div>
        </div>

        <div className="topbar__meta">
          <span className={`pill ${online ? 'pill--ok' : 'pill--err'}`} title={healthError ?? ''}>
            <i className="pill__dot" />
            后端 {online ? 'UP' : '不可达'}
          </span>
          {health ? (
            <span className="pill">
              依赖{' '}
              {Object.entries(health.dependencies ?? {})
                .map(([name, status]) => `${name}:${status}`)
                .join(' · ')}
            </span>
          ) : null}
          <span className="pill mono" title="本次会话标识">
            {SESSION_ID}
          </span>
          <span className="pill mono" title="上报方应用标识">
            {APP_ID}@{APP_RELEASE}
          </span>
          <a className="btn" href={SWAGGER_URL} target="_blank" rel="noreferrer">
            接口文档 ↗
          </a>
        </div>
      </header>

      {healthError ? (
        <div className="alert alert--block">
          连不上后端（{healthError}）。请先启动：
          <code className="mono">
            {' '}
            cd server &amp;&amp; mvn -pl :sts-app-server spring-boot:run{' '}
          </code>
        </div>
      ) : null}

      <main className={`layout${selected ? ' layout--with-drawer' : ''}`}>
        <GeneratorPanel
          records={records}
          reporterState={reporterState}
          autoReport={autoReport}
          lastTraceId={lastTraceId}
          onToggleAutoReport={setAutoReport}
          onClear={clearLocalRecords}
          onEmitLevel={handleEmitLevel}
          onBurst={handleBurst}
          onSimulateChain={handleSimulateChain}
          onRaiseError={handleRaiseError}
          onViewTrace={handleViewTrace}
        />

        <ServerLogPanel
          stats={stats}
          page={page}
          loading={loading}
          error={queryError}
          filters={filters}
          onFiltersChange={(next) => {
            setFilters(next);
            setPageNo(1);
          }}
          onPageChange={setPageNo}
          onRefresh={() => setRefreshTick((n) => n + 1)}
          onSelect={(log) => {
            setSelected(log);
            setTrace(null);
          }}
          onViewTrace={handleViewTrace}
        />

        <LogDetailDrawer
          log={selected}
          trace={trace}
          traceLoading={traceLoading}
          onClose={() => {
            setSelected(null);
            setTrace(null);
          }}
          onLoadTrace={handleViewTrace}
          onClearTrace={() => setTrace(null)}
          onSelect={(log) => {
            setSelected(log);
            setTrace(null);
          }}
        />
      </main>
    </div>
  );
}
