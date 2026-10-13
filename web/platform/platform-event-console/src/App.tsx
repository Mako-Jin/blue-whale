import type { ClientEventPage, ClientEventStats, Health } from 'common-api';
import { EventConsoleViews } from 'common-api';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import './App.less';
import { usePermission } from './auth/permissions';
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
  /*
   * 两栏各自的菜单码 —— 与接口权限是两层：这决定"这一栏出不出来"，
   * 不决定里面的请求能不能成功（右栏还要 `event:client:read`，服务端说了算）。
   *
   * 拆成两栏而不是一个整应用的码，是因为它们的依赖本来就不同：
   * 左栏全在前端完成、不需要任何后端权限；右栏要读服务端库、能读到别人的行为数据。
   */
  const canTrack = usePermission(EventConsoleViews.TRACK);
  const canQuery = usePermission(EventConsoleViews.QUERY);

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
    // 左栏不可见时不上报：一条看不见的管道仍在后台发请求，就是"假开关"那种味道 ——
    // 界面上没有它，行为上却处处是它
    if (autoReport && canTrack) channel.start();
    else channel.stop();
    return () => channel.stop();
  }, [autoReport, canTrack]);

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
    // 右栏不可见就不查：这时必然失败（没有 event:client:read 会拿到 40301），
    // 白留一屏错误，也会在审计里多出几条"其实没人看的查询"
    if (!canQuery) {
      return;
    }

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
  }, [eventNameFilter, pageNo, refreshTick, canQuery]);

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

      {/*
        两栏都没有时不能留一片空白：那既看不出是"没权限"还是"坏了"，
        也看不出该找谁。这里指名说出缺的是哪一类码。
      */}
      {!canTrack && !canQuery ? (
        <p className="alert alert--block" data-testid="event-empty">
          当前身份没有可见的区域（需要 <code>ui:event_track:view</code> 或{' '}
          <code>ui:event_query:view</code> 菜单码）。
        </p>
      ) : null}

      {/*
        只剩一栏时把列定义整体换成单列 —— 否则那一栏会被按两栏的比例挤在半边，
        右边留一片空白，看起来像"右栏加载失败"。
      */}
      <main className={`layout${canTrack && canQuery ? '' : ' layout--single'}`}>
        {canTrack ? (
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
        ) : null}

        {canQuery ? (
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
        ) : null}
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
