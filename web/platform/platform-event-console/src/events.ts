import { createApiClient, createConsoleAuth } from 'common-api';
import type { ClientEventItem, ClientEventReportBody, ClientEventReportResult } from 'common-api';
import { createTracker } from 'common-tools/events';
import type { EventEnvelope, JsonObject } from 'common-tools';
import { BatchChannel } from 'common-tools/report';
import type { BatchChannelState } from 'common-tools/report';

/** 上报方应用标识，会随每批事件落库，用于区分来源 */
export const APP_ID = 'platform-event-console';

/** 应用版本 */
export const APP_RELEASE = '0.1.0';

/** 本次会话标识 */
export const SESSION_ID = `s-${Math.random().toString(36).slice(2, 10)}`;

/** 属性结构版本。本页所有事件的 props 结构都是第一版 */
const PROPS_VERSION = 1;

/**
 * 匿名设备标识。
 *
 * <p>写进 localStorage 是为了模拟"跨会话的稳定身份"：登录前只有它，
 * 登录后才有 `userId`；分析侧靠 deviceId 把匿名期的浏览与登录后的下单缝成同一个人。
 * 每次刷新都换一个随机值的话，漏斗的入口永远对不上出口。
 */
const DEVICE_KEY = 'bw-event-console:device-id';

function resolveDeviceId(): string {
  try {
    const existing = window.localStorage.getItem(DEVICE_KEY);
    if (existing) return existing;
    const created = `d-${Math.random().toString(36).slice(2, 12)}`;
    window.localStorage.setItem(DEVICE_KEY, created);
    return created;
  } catch {
    // 隐私模式 / 禁用存储下 localStorage 会直接抛错。退化成会话级标识而不是让页面崩掉 ——
    // "取不到设备号"不该把整条埋点链路带走
    return `d-ephemeral-${Math.random().toString(36).slice(2, 8)}`;
  }
}

export const DEVICE_ID = resolveDeviceId();

/**
 * 认证端口。
 *
 * <p><b>本应用的查询接口都是要鉴权的</b>（`event:client:read`），因此必须带令牌；
 * 上报接口是公开的，带不带都能成功。
 * 令牌来源由 `createConsoleAuth` 统一决定：基座内用基座下发的认证能力，
 * 独立运行时用自建会话（见 `common-api` 的 `console-auth`）。
 *
 * <p>凭据只在 `import.meta.env.DEV` 下用于独立运行的自动登录，生产构建不会启用。
 */
export const consoleAuth = createConsoleAuth({
  name: 'event-console',
  // 只有开发构建才允许自动登录；生产构建即便被独立部署也只会记一条错误日志
  allowDevAutoLogin: import.meta.env.DEV,
  devAccount: {
    tenantCode: import.meta.env.VITE_DEV_TENANT_CODE ?? 'default',
    username: import.meta.env.VITE_DEV_USERNAME ?? 'admin',
    password: import.meta.env.VITE_DEV_PASSWORD ?? 'Admin@12345',
  },
});

export const api = createApiClient({
  // provider 是请求时求值，所以可以在 mount 之前就装配好
  accessTokenProvider: () => consoleAuth.port.getAccessToken(),
  onUnauthorized: () => consoleAuth.port.reauthenticate(),
});

/* ------------------------------------------------------- 屏蔽与脱敏中间件 */

/**
 * 被屏蔽的事件名。命中即**在通道之前**丢弃 —— 一条都不会进入缓冲，更不会离开浏览器。
 *
 * <p>这是"用中间件表达屏蔽"的价值所在：屏蔽逻辑只有一处，
 * 而不是散落在每个调用点的 `if` 里。
 */
const blockedNames = new Set<string>();

const blockedListeners = new Set<() => void>();

function blockMiddleware(
  event: EventEnvelope,
  next: (event: EventEnvelope) => EventEnvelope | null,
): EventEnvelope | null {
  return blockedNames.has(event.eventName) ? null : next(event);
}

/**
 * 需要脱敏的属性名。
 *
 * <p>脱敏放在**进入通道之前**，因此明文从未离开浏览器，也从未出现在任何一次
 * 网络请求或服务端日志里。放到服务端做就晚了 —— 那时明文已经走完了整条链路。
 */
const MASKED_KEYS = new Set(['phone', 'password', 'idCard', 'email']);

function maskText(value: string): string {
  if (value.length <= 4) return '***';
  const head = value.slice(0, 3);
  const tail = value.slice(-2);
  return `${head}${'*'.repeat(Math.min(6, value.length - 5))}${tail}`;
}

function maskMiddleware(
  event: EventEnvelope,
  next: (event: EventEnvelope) => EventEnvelope | null,
): EventEnvelope | null {
  if (!event.props) return next(event);

  let changed = false;
  const masked: JsonObject = {};
  for (const [key, value] of Object.entries(event.props)) {
    if (MASKED_KEYS.has(key)) {
      masked[key] = typeof value === 'string' ? maskText(value) : '***';
      changed = true;
    } else {
      masked[key] = value;
    }
  }
  return next(changed ? { ...event, props: masked } : event);
}

/* ---------------------------------------------------------------- 追踪器 */

/** 埋点自身的异常记录。埋点坏了要能被看见，但绝不能向上抛 */
export interface TrackerError {
  stage: string;
  message: string;
  at: number;
}

const trackerErrors: TrackerError[] = [];

const trackerErrorListeners = new Set<() => void>();

/**
 * 事件追踪器。
 *
 * <p>中间件的顺序即执行顺序：先按名单**屏蔽**（最便宜，能省掉后面全部工作），
 * 再**脱敏**（改写 props），最后才交给订阅者。
 */
export const tracker = createTracker({
  context: {
    sessionId: SESSION_ID,
    deviceId: DEVICE_ID,
    release: APP_RELEASE,
    route: '/',
  },
  middlewares: [blockMiddleware, maskMiddleware],
  onError: (error, stage) => {
    trackerErrors.unshift({
      stage,
      message: error instanceof Error ? error.message : String(error),
      at: Date.now(),
    });
    if (trackerErrors.length > 20) trackerErrors.length = 20;
    for (const listener of trackerErrorListeners) listener();
  },
});

/* -------------------------------------------------------------- 上报通道 */

/**
 * 卸载兜底发送。
 *
 * <p><b>这是埋点最大的数据损失来源：</b>定时器还没到点，缓冲里那一批就随页面消失，
 * 而"用户离开"往往正是最值得记录的行为。
 *
 * <p>两个必须绕开的坑：
 * <ol>
 *   <li><b>只能用 `fetch(..., { keepalive: true })`，不能用 `navigator.sendBeacon` 发 JSON。</b>
 *       sendBeacon 里塞 `application/json` 的 Blob 会触发 CORS 预检，而预检在卸载阶段
 *       不可能完成 —— 请求被静默丢弃。keepalive fetch 在同源下没有这个问题。</li>
 *   <li><b>keepalive 的请求体上限是 64KB</b>，超了整个请求会被静默丢掉（不报错）。
 *       因此这里按体积裁剪，并优先保住**最近**的那批。</li>
 * </ol>
 */
const KEEPALIVE_SAFE_BYTES = 50 * 1024;

/** 从最新往前收，直到达到体积预算 */
function trimToBudget(events: ClientEventItem[], budget: number): ClientEventItem[] {
  const kept: ClientEventItem[] = [];
  let size = 0;
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const item = events[i];
    if (!item) break;
    const length = JSON.stringify(item).length;
    if (size + length > budget) break;
    size += length;
    kept.unshift(item);
  }
  return kept;
}

function sendOnUnload(body: ClientEventReportBody): boolean {
  // 越晚发生的事件越接近"用户为什么离开"这个问题的答案，因此超预算时从最旧的开始丢
  const events = trimToBudget(body.events, KEEPALIVE_SAFE_BYTES);
  if (events.length === 0) {
    // 一条都放不下：放弃，而不是发一个必然被丢弃的请求
    return true;
  }
  try {
    void fetch('/api/events/client', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...body, events }),
      keepalive: true,
    });
    return true;
  } catch {
    return false;
  }
}

const channelListeners = new Set<(state: BatchChannelState) => void>();

export const channel = new BatchChannel<
  ClientEventItem,
  ClientEventReportBody,
  ClientEventReportResult
>({
  // 批级信封：同一批事件必然同源，逐条重复携带 app / sessionId 是纯粹的浪费
  buildBody: (events) => ({ app: APP_ID, release: APP_RELEASE, sessionId: SESSION_ID, events }),
  send: (body) => api.event.report(body),
  resolveCounts: (result, batch) => ({
    accepted: result?.accepted ?? batch.length,
    rejected: result?.rejected ?? 0,
  }),
  batchSize: 20,
  flushIntervalMs: 3000,
  maxBuffer: 500,
  onChange: (state) => {
    for (const listener of channelListeners) listener(state);
  },
  sendOnUnload,
});

/**
 * 订阅通道状态。
 *
 * <p>订阅时**立即回调一次当前状态**：只在变化时通知的话，页面首帧会显示全 0，
 * 而那时缓冲里可能已经有数据了 —— 首屏数字与实际不符，最容易被当成 bug。
 */
export function onChannelChange(listener: (state: BatchChannelState) => void): () => void {
  channelListeners.add(listener);
  listener(channel.getState());
  return () => {
    channelListeners.delete(listener);
  };
}

export function onTrackerError(listener: () => void): () => void {
  trackerErrorListeners.add(listener);
  listener();
  return () => {
    trackerErrorListeners.delete(listener);
  };
}

export function getTrackerErrors(): readonly TrackerError[] {
  return trackerErrors;
}

/* -------------------------------------------------- 客户端本地事件缓冲 */

const MAX_LOCAL_EVENTS = 200;

const localEvents: EventEnvelope[] = [];

const localListeners = new Set<() => void>();

/**
 * 订阅者一号：本地缓冲（页面左侧的"客户端视角"）。
 * 订阅者二号：上报通道。两者互不知道对方存在 —— 这正是总线的意义。
 */
tracker.subscribe((event) => {
  localEvents.unshift(event);
  if (localEvents.length > MAX_LOCAL_EVENTS) localEvents.length = MAX_LOCAL_EVENTS;
  channel.push(toReportItem(event));

  for (const listener of localListeners) listener();
});

export function getLocalEvents(): readonly EventEnvelope[] {
  return localEvents;
}

export function clearLocalEvents(): void {
  localEvents.length = 0;
  for (const listener of localListeners) listener();
}

export function onLocalEventsChange(listener: () => void): () => void {
  localListeners.add(listener);
  return () => {
    localListeners.delete(listener);
  };
}

/* ------------------------------------------------------------------ 转换 */

/**
 * EventEnvelope → 上报条目。
 *
 * <p>字段名与契约一一对应，这里只做透传与改名：信封里叫 `clientTime`，
 * 契约里叫 `timestamp`（与日志域的 `ClientLogItem` 保持一致）。
 */
function toReportItem(event: EventEnvelope): ClientEventItem {
  return {
    eventId: event.eventId,
    eventName: event.eventName,
    userId: event.userId,
    deviceId: event.deviceId,
    sessionId: event.sessionId,
    route: event.route,
    release: event.release,
    props: event.props,
    propsVersion: event.propsVersion,
    timestamp: event.clientTime,
  };
}

/* -------------------------------------------------------------- 业务动作 */

export function login(userId: string): void {
  tracker.updateContext({ userId });
  // 顺序有讲究：先把身份写进上下文，再发事件 —— 反过来的话，
  // "登录成功"这条事件会记在上一个身份（或匿名）名下。
  tracker.track('user.login', { userId }, { propsVersion: PROPS_VERSION });
}

export function logout(): void {
  // 反过来：先发事件（此时上下文里还有 userId），再清身份。
  // 先清后发的话，登出事件会变成匿名的，而"谁登出了"恰恰是审计与风控要的。
  tracker.track('user.logout', {}, { propsVersion: PROPS_VERSION });
  tracker.clearUser();
}

export function navigate(route: string): void {
  tracker.updateContext({ route });
  tracker.track('page.view', { route }, { propsVersion: PROPS_VERSION });
}

/** 模拟一次带敏感字段的浏览行为，用来演示脱敏中间件 */
export function viewProfile(): void {
  tracker.track(
    'profile.contact.view',
    { phone: '13800138000', email: 'captain@bluewhale.dev', plan: 'pro' },
    { propsVersion: PROPS_VERSION },
  );
}

export function blockEvent(eventName: string): void {
  blockedNames.add(eventName);
  for (const listener of blockedListeners) listener();
}

export function unblockEvent(eventName: string): void {
  blockedNames.delete(eventName);
  for (const listener of blockedListeners) listener();
}

export function isBlocked(eventName: string): boolean {
  return blockedNames.has(eventName);
}

export function onBlockedChange(listener: () => void): () => void {
  blockedListeners.add(listener);
  listener();
  return () => {
    blockedListeners.delete(listener);
  };
}

/** 演示用的事件目录。真实项目里这份清单就是"事件注册表"的雏形 */
export interface EventDefinition {
  name: string;
  label: string;
  props?: Record<string, unknown>;
  /** 说明这个事件在真实业务里由什么触发 */
  trigger: string;
}

export const EVENT_CATALOG: readonly EventDefinition[] = [
  {
    name: 'page.view',
    label: '浏览页面',
    props: { route: '/home', referrer: 'direct' },
    trigger: '路由变化时',
  },
  {
    name: 'search.query',
    label: '搜索',
    props: { keyword: 'blue whale', resultCount: 12 },
    trigger: '提交搜索时',
  },
  {
    name: 'cart.add',
    label: '加入购物车',
    props: { sku: 'BW-001', quantity: 2, price: 199 },
    trigger: '点击"加入购物车"',
  },
  {
    name: 'order.submit',
    label: '提交订单',
    props: { orderId: 'SO-2026-0001', amount: 398, channel: 'web' },
    trigger: '提交订单成功回调里',
  },
  {
    name: 'profile.contact.view',
    label: '查看联系方式（含敏感字段）',
    props: { phone: '13800138000', email: 'captain@bluewhale.dev' },
    trigger: '打开联系人卡片（演示脱敏）',
  },
];

/** 发一条目录里的事件 */
export function fireEvent(name: string): EventEnvelope | null {
  const definition = EVENT_CATALOG.find((item) => item.name === name);
  return tracker.track(name, definition?.props, { propsVersion: PROPS_VERSION });
}
