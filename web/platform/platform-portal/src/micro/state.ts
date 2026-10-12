import { initGlobalState, type MicroAppStateActions } from 'qiankun';
import { createLogger } from 'common-tools';
import type { GlobalState } from './types';

const logger = createLogger('micro:state');

/**
 * 初始全局状态。子应用在 mount 时用 `onGlobalStateChange(cb, true)` 拿到这一份。
 */
export const INITIAL_GLOBAL_STATE: GlobalState = {
  theme: 'light',
  locale: 'zh-CN',
  userId: null,
  roles: [],
  permissions: [],
};

const actions: MicroAppStateActions = initGlobalState(INITIAL_GLOBAL_STATE);

/**
 * 本地状态副本。
 *
 * 存在的唯一理由是 qiankun 的 `initGlobalState` **不返回 `getGlobalState`**。
 * 参考项目 `platform-terminator` 把 `actions.getGlobalState` 直接传给了子应用，
 * 而那个属性是 `undefined` —— 子应用一调用就抛错，且要到运行时才暴露。
 * 这里自己维护一份副本，把同一个坑填掉。
 */
let currentState: GlobalState = { ...INITIAL_GLOBAL_STATE };

actions.onGlobalStateChange((state) => {
  currentState = { ...currentState, ...(state as GlobalState) };
}, true);

export function getGlobalState(): GlobalState {
  return { ...currentState };
}

export function setGlobalState(partial: Partial<GlobalState>): void {
  const next: GlobalState = { ...currentState, ...partial };
  logger.info('下发全局状态', next);
  actions.setGlobalState(next);
}

/**
 * 包一层而不是直接导出 `actions.onGlobalStateChange`：
 * 裸引用会脱离调用上下文，将来 qiankun 内部实现一改成依赖 `this` 就静默失效。
 */
export function onGlobalStateChange(
  callback: (state: GlobalState, prevState: GlobalState) => void,
  fireImmediately?: boolean,
): void {
  actions.onGlobalStateChange(
    callback as Parameters<typeof actions.onGlobalStateChange>[0],
    fireImmediately,
  );
}

export function offGlobalStateChange(): void {
  actions.offGlobalStateChange();
}
