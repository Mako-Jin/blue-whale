// qiankun 基座入口 - 注册微应用并监听路由变化

import { AppEvents, createLogger, eventBus } from '@bluewhale/whale-common-tools';
import { registerMicroApps, start, addGlobalUncaughtErrorHandler } from 'qiankun';
import { apps } from './apps';

const logger = createLogger('whale-platform › qiankun');

type SubAppErrorListener = (appName: string) => void;

const subAppErrorListeners = new Set<SubAppErrorListener>();

// 订阅子应用加载失败事件，返回取消订阅函数（供 SubAppContainer 渲染错误兜底 UI）
export function onSubAppError(listener: SubAppErrorListener): () => void {
  subAppErrorListeners.add(listener);
  return () => {
    subAppErrorListeners.delete(listener);
  };
}

function notifySubAppError(message: string): void {
  const failed = apps.find((app) => message.includes(app.name));
  if (!failed) return;
  logger.error(`子应用 ${failed.name} 加载失败`, message);
  // 审计：子应用加载失败必须留痕，便于定位发布/资源 404 问题
  eventBus.emit(AppEvents.SUB_APP_ERROR, { appName: failed.name, message });
  subAppErrorListeners.forEach((listener) => listener(failed.name));
}

export function initQiankun(): void {
  registerMicroApps(apps);

  addGlobalUncaughtErrorHandler((event) => {
    const msg = event instanceof Error ? event.message : typeof event === 'string' ? event : '';
    if (msg) {
      notifySubAppError(msg);
    } else {
      logger.error('qiankun global error', event);
    }
  });

  // 注：qiankun initGlobalState 已移除——全仓无任何子应用消费 globalState，
  // 登录态统一经 apps.ts 的 props 注入（getToken/getUserId/getPermissions）。

  // 延迟启动：容器渲染出来之后再 start（详见 startQiankun 注释）
  waitContainerThenStart();
}

/** 子应用挂载容器的 DOM id（与 SubAppContainer 保持一致） */
const CONTAINER_ID = 'subapp-container';

/** start() 是否已执行（qiankun 只允许 start 一次） */
let started = false;

/** 等待容器的轮询间隔与上限（100 × 50ms = 5s） */
const POLL_INTERVAL_MS = 50;
const MAX_POLL_TIMES = 100;

/**
 * 启动 qiankun（幂等）。
 *
 * <p>调用时机很关键：qiankun 在 {@code start()} 时会按当前 URL 立即激活匹配的应用，
 * 若此刻基座还没有渲染出 {@code #subapp-container}（深链接刷新时 React 尚在鉴权与布局
 * 渲染中），激活会直接抛「Target container ... not existed」，页面白屏且需手动刷新。
 * 因此 start 必须发生在容器挂到 DOM 之后：{@code SubAppContainer} 在 useEffect（DOM 已提交）
 * 里调用本函数，同时 {@code waitContainerThenStart} 做首次进入的兜底轮询。</p>
 */
export function startQiankun(): void {
  if (started) return;
  started = true;
  start({
    sandbox: { experimentalStyleIsolation: true },
    singular: true,
    // 按需加载：默认首页为基座工作台，不渲染任何子应用；
    // 全量预取会在首屏并发拉取 9 个子应用 entry（T-10），改为首次激活时才加载。
    prefetch: false,
  });
  logger.info('qiankun started');
}

/**
 * 轮询等待容器出现后启动；超时也启动（首页无容器时先让 qiankun 就绪，
 * 之后由路由变化与 SubAppContainer 的 ensure 调用接管）。
 *
 * @param attempt 已轮询次数（递归用）
 */
function waitContainerThenStart(attempt = 0): void {
  if (document.getElementById(CONTAINER_ID) || attempt >= MAX_POLL_TIMES) {
    startQiankun();
    return;
  }
  window.setTimeout(() => waitContainerThenStart(attempt + 1), POLL_INTERVAL_MS);
}
