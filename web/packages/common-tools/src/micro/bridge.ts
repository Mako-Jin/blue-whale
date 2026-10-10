import { createLogger } from '../logger';
import type { HostGlobalState, HostProps } from './types';

const logger = createLogger('micro:host');

/** 调试标记的 DOM id，卸载时按它清理。 */
const INDICATOR_ID = 'micro-host-indicator';

/** 全局状态订阅者。 */
export type GlobalStateListener = (state: HostGlobalState) => void;

let hostProps: HostProps | null = null;

/**
 * 最近一次由基座推来的状态。
 *
 * 存在意义是兜底：`HostProps.getGlobalState` 是**可选**的，基座若不提供，
 * `getHostGlobalState()` 就只剩这一份可读。
 */
let latestState: HostGlobalState = {};

/**
 * 本地订阅者集合。
 *
 * **这层多路复用必须由桥来做**，不能把 `props.onGlobalStateChange` 直接交给调用方。
 * qiankun 为每个子应用只保存**一个**回调，第二次注册会直接覆盖第一个
 * （qiankun 源码原话：「每个子应用只有一个激活状态的全局监听，新监听覆盖旧监听」，
 * 而且只打一条 `console.warn`，不报错）。子应用里通常不止一个消费者 ——
 * 调试标记、业务状态、埋点上下文 —— 谁后注册谁生效，先注册的那个**静默**失效，
 * 典型症状就是"页面上的值变了，右下角的标记没变"。
 *
 * 桥在 `bindHost` 时只注册一次原生回调，再分发给这里的所有订阅者。
 */
const listeners = new Set<GlobalStateListener>();

function dispatch(state: HostGlobalState): void {
  // 先复制再遍历：订阅者在回调里取消订阅（React 卸载时的常见写法）不影响本次分发
  Array.from(listeners).forEach((listener) => listener(state));
}

/** 当前是否运行在基座里（`false` 表示独立运行）。 */
export function isMicroApp(): boolean {
  return hostProps !== null;
}

export function getHostProps(): HostProps | null {
  return hostProps;
}

/**
 * 读取基座下发的全局状态。
 *
 * 优先问基座的 getter（那是权威值），基座没提供时退回最近一次推送的状态；
 * 独立运行、且从未收到推送时返回空对象。
 */
export function getHostGlobalState(): HostGlobalState {
  return hostProps?.getGlobalState?.() ?? latestState;
}

/** 主动向基座回写全局状态（例如子应用内登录后补齐 userId）。 */
export function setHostGlobalState(state: Partial<HostGlobalState>): void {
  hostProps?.setGlobalState?.(state);
}

/**
 * 订阅基座下发的全局状态，**返回取消订阅的函数**。
 *
 * 子应用一律用这个而不是 `props.onGlobalStateChange` —— 后者每个应用只有一个槽位，
 * 多个消费者会互相顶掉，原因见上面 `listeners` 的注释。
 *
 * 默认**不**立即回调。这不是偷懒：React 消费者普遍把订阅写在 `useEffect` 里，
 * 而 effect 里同步 setState 会多一次无谓渲染（本项目还把它列为 lint 违规）。
 * 需要初值的调用方（例如右下角的调试标记）显式传 `fireImmediately: true`。
 */
export function onHostGlobalStateChange(
  listener: GlobalStateListener,
  options: { fireImmediately?: boolean } = {},
): () => void {
  listeners.add(listener);

  if (options.fireImmediately) {
    listener(getHostGlobalState());
  }

  return () => {
    listeners.delete(listener);
  };
}

/**
 * 接管基座下发的 props。在子应用的 `mount` 里调用。
 *
 * 除了保存 props，还做两件事，都是为了"能调试"：
 * 1. 把 props 挂到 `window.__MICRO_HOST__` —— 在 DevTools 里可以直接看基座到底给了什么，
 *    排查"props 到底传到没有"时不用打断点；
 * 2. 画面右下角挂一个状态标记 —— 全局状态下发是本项目微前端要验证的核心能力之一，
 *    只靠 console 输出容易看漏，摆在页面上才有说服力。
 */
export function bindHost(props: HostProps, appName: string): void {
  hostProps = props;

  (window as unknown as Record<string, unknown>).__MICRO_HOST__ = props;

  /**
   * **全应用唯一的原生回调。**
   *
   * `fireImmediately: true` 让 qiankun 立刻把当前状态推过来，`latestState` 因此有值 ——
   * 即使基座没实现 `getGlobalState`，调试标记也不会退化成 `theme=-`。
   */
  props.onGlobalStateChange?.((state) => {
    latestState = state;
    logger.info('收到全局状态更新', state);
    dispatch(state);
  }, true);

  // 调试标记也是一个普通订阅者：和业务状态走同一条分发路径，
  // 就不会再出现"两个监听互相覆盖"的局面。
  onHostGlobalStateChange(
    (state) => {
      renderIndicator(appName, state);
    },
    { fireImmediately: true },
  );
}

/** 断开与基座的连接。在子应用的 `unmount` 里调用。 */
export function unbindHost(): void {
  // 先取父节点、并留住 props 引用，再清空 —— 反过来的话会退化成 document.body，
  // 容器里的标记就搜不到了，留下一个"卸载不干净"的假象。
  const parent = getIndicatorParent();
  const props = hostProps;
  hostProps = null;

  parent?.querySelector(`#${INDICATOR_ID}`)?.remove();
  document.getElementById(INDICATOR_ID)?.remove();
  delete (window as unknown as Record<string, unknown>).__MICRO_HOST__;

  // 谁注册谁注销：桥注册了原生回调，就由桥收回，不指望调用方记得。
  // qiankun 卸载时自己也会注销一次，这里是幂等的重复保护。
  props?.offGlobalStateChange?.();

  /**
   * 订阅者一并清空。
   *
   * 这条比看起来重要：子应用的模块在多次挂载之间是**复用**的（模块级变量不重置），
   * 上一轮若漏了解绑，下一轮同一个回调就会被执行两次、三次……
   */
  listeners.clear();
  latestState = {};
}

function getIndicatorParent(): Element | null {
  // 优先挂进基座给的容器：容器卸载时会被 qiankun 清空，标记跟着一起走，
  // 不会出现"子应用已经卸载但标记还留在页面上"的错觉。
  return hostProps?.container ?? (typeof document === 'undefined' ? null : document.body);
}

function renderIndicator(appName: string, state: HostGlobalState): void {
  if (typeof document === 'undefined') {
    return;
  }

  const parent = getIndicatorParent();
  if (!parent) {
    return;
  }

  let node = document.getElementById(INDICATOR_ID);
  if (!node) {
    node = document.createElement('div');
    node.id = INDICATOR_ID;
    node.style.cssText = [
      'position:fixed',
      'right:16px',
      'bottom:16px',
      'z-index:9999',
      'padding:6px 10px',
      'font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace',
      'color:#1f2329',
      'background:#fff',
      'border:1px solid #165dff',
      'border-radius:6px',
      'box-shadow:0 2px 8px rgb(0 0 0 / 12%)',
      'pointer-events:none',
      'white-space:nowrap',
    ].join(';');
    parent.appendChild(node);
  }

  node.textContent = `${appName} · theme=${state.theme ?? '-'} · userId=${state.userId ?? '(未登录)'}`;
}
