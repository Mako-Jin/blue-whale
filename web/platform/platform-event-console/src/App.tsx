import type { ClientEventPage, ClientEventStats, Health } from 'common-api';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import './App.less';
import { ServerEventPanel } from './components/ServerEventPanel';
import { TrackPanel } from './components/TrackPanel';
import {
  APP_ID,
  APP_RELEASE,
  DEVICE_ID,
  SESSION_ID,
  api,
  blockEvent,
  channel,
  clearLocalEvents,
  fireEvent,
  getLocalEvents,
  getTrackerErrors,
  isBlocked,
  login,
  logout,
  navigate,
  onBlockedChange,
  onChannelChange,
  onLocalEventsChange,
  onTrackerError,
  tracker,
  unblockEvent,
  viewProfile,
} from './events';
import type { TrackerError } from './events';

const PAGE_SIZE = 20;

/** 后端接口文档地址（由 springdoc 提供，与后端同源） */
const SWAGGER_URL = '/swagger-ui.html';

/** 演示用：这些事件可以在左栏被"屏蔽"，用来观察中间件在通道之前把事件丢掉 */
const BLOCKABLE = ['page.view', 'search.query', 'cart.add'] as const;

export default function App() {
  const [channelState, setChannelState] = useState(() => channel.getState());
  const [localTick, setLocalTick] = useState(0);
  const [autoReport, setAutoReport] = useState(true);
  const [blockedTick, setBlockedTick] = useState(0);
  const [errorTick, setErrorTick] = useState(0);

  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [route, setRoute] = useState('/');

  const [health, setHealth] = useState<Health | null>(null);
  const [healthError, setHealthError] = useState<string | undefined>(undefined);

  const [stats, setStats] = useState<ClientEventStats | null>(null);
  const [page, setPage] = useState<ClientEventPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [eventNameFilter, setEventNameFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [queryError, setQueryError] = useState<string | undefined>(undefined);
  const [refreshTick, setRefreshTick] = useState(0);

  /* ------------------------------------------------------------- 订阅 */

  useEffect(() => onChannelChange(setChannelState), []);
  useEffect(() => onLocalEventsChange(() => setLocalTick((n) => n + 1)), []);
  useEffect(() => onBlockedChange(() => setBlockedTick((n) => n + 1)), []);
  useEffect(() => onTrackerError(() => setErrorTick((n) => n + 1)), []);

  /* ------------------------------------------- 上报开关 → 启动/停止缓冲 */

  useEffect(() => {
    if (autoReport) channel.start();
    else channel.stop();
    return () => channel.stop();
  }, [autoReport]);

  /* ------------------------------------- 卸载兜底：绑定 pagehide / 切后台 */

  useEffect(() => channel.bindUnload(), []);

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
          api.event.stats({}),
          api.event.page({
            eventName: eventNameFilter.trim() === '' ? undefined : eventNameFilter.trim(),
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
  }, [eventNameFilter, pageNo, refreshTick]);

  /* --------------------------- 上报成功后自动刷新，让"左边产生右边出现"可见 */

  const lastSent = useRef(0);
  useEffect(() => {
    if (channelState.sent > lastSent.current) {
      lastSent.current = channelState.sent;
      setRefreshTick((n) => n + 1);
    }
  }, [channelState.sent]);

  /* ----------------------------------------------------------- 交互 */

  const handleFire = useCallback((name: string) => {
    fireEvent(name);
  }, []);

  const handleLogin = useCallback(() => {
    // 用随机 id 模拟"换了一个人"，这样重复点击也能看出身份确实换了
    const next = `u-${Math.random().toString(36).slice(2, 6)}`;
    login(next);
    setUserId(next);
  }, []);

  const handleLogout = useCallback(() => {
    logout();
    setUserId(undefined);
  }, []);

  const handleNavigate = useCallback((next: string) => {
    navigate(next);
    setRoute(next);
  }, []);

  const handleToggleBlock = useCallback((name: string) => {
    // 屏蔽名单是模块级的（它活在中间件里），不进 React state ——
    // 否则"UI 显示的名单"与"真正生效的名单"会成为两份会各说各话的状态
    if (isBlocked(name)) unblockEvent(name);
    else blockEvent(name);
  }, []);

  const events = useMemo(() => {
    void localTick;
    return getLocalEvents();
  }, [localTick]);

  const trackerErrors: readonly TrackerError[] = useMemo(() => {
    void errorTick;
    return getTrackerErrors();
  }, [errorTick]);

  const blocked = useMemo(() => {
    void blockedTick;
    return BLOCKABLE.filter((name) => isBlocked(name));
  }, [blockedTick]);

  const online = health?.status === 'UP';
  const contextSummary = tracker.getContext();

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo" aria-hidden="true" />
          <div>
            <h1 className="topbar__title">BlueWhale 行为事件控制台</h1>
            <p className="topbar__subtitle">
              埋点 → 上报 → 落库 → 行为分析的端到端演示（与日志管道并列的第二条数据管道）
            </p>
          </div>
        </div>

        <div className="topbar__meta">
          <span className={`pill ${online ? 'pill--ok' : 'pill--err'}`} title={healthError ?? ''}>
            <i className="pill__dot" />
            后端 {online ? 'UP' : '不可达'}
          </span>
          <span className="pill mono" title="匿名设备标识（跨会话稳定）">
            {DEVICE_ID}
          </span>
          <span className="pill mono" title="会话标识">
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

      <main className="layout">
        <TrackPanel
          events={events}
          channelState={channelState}
          autoReport={autoReport}
          userId={contextSummary.userId}
          route={route}
          blocked={blocked}
          trackerErrors={trackerErrors}
          onToggleAutoReport={setAutoReport}
          onClear={clearLocalEvents}
          onFire={handleFire}
          onLogin={handleLogin}
          onLogout={handleLogout}
          onNavigate={handleNavigate}
          onToggleBlock={handleToggleBlock}
          onViewProfile={viewProfile}
        />

        <ServerEventPanel
          stats={stats}
          page={page}
          loading={loading}
          error={queryError}
          eventNameFilter={eventNameFilter}
          onEventNameFilterChange={(value) => {
            setEventNameFilter(value);
            setPageNo(1);
          }}
          onPageChange={setPageNo}
          onRefresh={() => setRefreshTick((n) => n + 1)}
        />
      </main>

      {/* 上下文摘要单独一行：这一行是"身份缝合"的现场证据 ——
          登录后 userId 出现、deviceId 不变，两者同时挂在新产生的事件上 */}
      <footer className="contextbar">
        <span className="contextbar__title">当前事件上下文</span>
        <code className="mono">
          sessionId={contextSummary.sessionId} · deviceId={contextSummary.deviceId ?? '-'} · userId=
          {userId ?? '（未登录）'} · route={route} · release={contextSummary.release ?? '-'}
        </code>
      </footer>
    </div>
  );
}
