import { createLogger } from '../logger';
import type { HostGlobalState, HostProps } from './types';

const logger = createLogger('micro:host');

/** 调试标记的 DOM id，卸载时按它清理。 */
const INDICATOR_ID = 'micro-host-indicator';

let hostProps: HostProps | null = null;

/** 当前是否运行在基座里（`false` 表示独立运行）。 */
export function isMicroApp(): boolean {
  return hostProps !== null;
}

export function getHostProps(): HostProps | null {
  return hostProps;
}

/** 读取基座下发的全局状态。独立运行、或基座没提供时返回空对象。 */
export function getHostGlobalState(): HostGlobalState {
  return hostProps?.getGlobalState?.() ?? {};
}

/** 主动向基座回写全局状态（例如子应用内登录后补齐 userId）。 */
export function setHostGlobalState(state: Partial<HostGlobalState>): void {
  hostProps?.setGlobalState?.(state);
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

  props.onGlobalStateChange?.((state) => {
    logger.info('收到全局状态更新', state);
    renderIndicator(appName, state);
  }, true);

  renderIndicator(appName, getHostGlobalState());
}

/** 断开与基座的连接。在子应用的 `unmount` 里调用。 */
export function unbindHost(): void {
  // 先取父节点再清空 hostProps —— 反过来的话会退化成 document.body，
  // 容器里的标记就搜不到了，留下一个"卸载不干净"的假象。
  const parent = getIndicatorParent();
  hostProps = null;

  parent?.querySelector(`#${INDICATOR_ID}`)?.remove();
  document.getElementById(INDICATOR_ID)?.remove();
  delete (window as unknown as Record<string, unknown>).__MICRO_HOST__;
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
