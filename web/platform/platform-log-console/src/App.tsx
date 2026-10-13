import type { Health, ClientLogPage, ClientLogStats, ClientLogVO } from 'common-api';
import { LogConsoleViews } from 'common-api';
import type { LogLevel } from 'common-tools';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import './App.less';
import { usePermission } from './auth/permissions';
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
  /*
   * 两栏各自的菜单码 —— 与接口权限是两层：这决定"这一栏出不出来"，
   * 不决定里面的请求能不能成功（右栏还要 `log:client:read`，服务端说了算）。
   *
   * 拆成两栏而不是一个整应用的码，是因为它们的依赖本来就不同：
   * 左栏全在前端完成、不需要任何后端权限；右栏要读服务端库、能读到别人的报错正文。
   */
  const canReport = usePermission(LogConsoleViews.REPORTER);
  const canQuery = usePermission(LogConsoleViews.QUERY);

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
    // 左栏不可见时不上报：一条看不见的管道仍在后台发请求，就是"假开关"那种味道 ——
    // 界面上没有它，行为上却处处是它
    if (autoReport && canReport) reporter.start();
    else reporter.stop();
    return () => reporter.stop();
  }, [autoReport, canReport]);

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
    // 右栏不可见就不查：这时必然失败（没有 log:client:read 会拿到 40301），
    // 白留一屏错误，也会在审计里多出几条"其实没人看的查询"
    if (!canQuery) {
      return;
    }

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
  }, [filters, pageNo, refreshTick, canQuery]);

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

      {/*
        两栏都没有时不能留一片空白：那既看不出是"没权限"还是"坏了"，
        也看不出该找谁。这里指名说出缺的是哪一类码。
      */}
      {!canReport && !canQuery ? (
        <p className="alert alert--block" data-testid="console-empty">
          当前身份没有可见的区域（需要 <code>ui:log_reporter:view</code> 或{' '}
          <code>ui:log_query:view</code> 菜单码）。
        </p>
      ) : null}

      {/*
        列定义跟着"可见栏数"走：只剩一栏时收成单列，免得被按两栏比例挤在半边。
        两个修饰类可能同时出现（只有右栏可见 + 抽屉打开），那种组合在 CSS 里单列一条规则。
      */}
      <main
        className={[
          'layout',
          canReport && canQuery ? '' : 'layout--single',
          selected && canQuery ? 'layout--with-drawer' : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {canReport ? (
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
        ) : null}

        {canQuery ? (
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
        ) : null}

        {/*
          详情抽屉跟着**右栏**走：它展示的是服务端日志与链路，
          没有右栏就点不开它 —— 单独给它一个码等于造一个进不去的入口。
        */}
        {canQuery ? (
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
        ) : null}
      </main>
    </div>
  );
}
