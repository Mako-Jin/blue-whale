import { useSyncExternalStore } from 'react';

import { createPermissionStore } from 'common-tools/micro';
import type { PermissionState } from 'common-tools/micro';

import { consoleAuth } from '../events';

/**
 * 权限快照 + React 适配 —— 本应用"谁在登录、能做什么"的唯一来源。
 *
 * <h3>store 在 `common-tools/micro`，这里只做两件事</h3>
 *
 * 一是把本应用的认证端口接上（{@link consoleAuth} 本身就是
 * {@link StandaloneIdentitySource}，被基座加载时它返回 null，于是 store 走基座分支）；
 * 二是把它接到 `useSyncExternalStore` 上 —— React 适配只有三行，
 * 而 `common-tools` 是一个与框架无关的内核，不该为这三行引入 React。
 *
 * <h3>为什么快照不是 Provider</h3>
 *
 * 权限是全应用共用的事实（两栏的显隐、以及将来可能的按钮判定都要用）。
 * 做成 context 也能用，但每个消费点都必须挂在 Provider 之下 ——
 * 而"挂在 Provider 之下"一旦漏了，症状是权限恒为假、两栏都不出现，
 * 排查方向会先指向"权限没下发"。
 *
 * <h3>本应用只把权限用在"看不看得到某一栏"上</h3>
 *
 * 两栏的接口权限本来就不一样：左栏（埋点演示）**不需要任何后端权限**，
 * 右栏（查服务端库）要 `event:client:read`。菜单码让"只做埋点演示、不给查询"
 * 成为一处配置，而不是一句只能靠约定维持的话。
 *
 * <p>与日志台同理，**演示区里的按钮永远都在**：菜单码只决定整栏出不出现。
 * 埋点演示的价值恰恰在于能亲手造出一条事件、再在右栏看着它落库 ——
 * 把按钮也按权限一个个藏起来，会让这一栏只剩一个标题。
 */
const store = createPermissionStore(consoleAuth);

/**
 * 接上权限来源，返回断开函数。
 *
 * <p>**必须在 `mount` 里调用、在 `unmount` 里断开**：子应用的模块在多次挂载之间
 * 是复用的，而基座桥在 `unbindHost` 时会清空全部订阅者 ——
 * 顶层订阅在第二次挂载后就不会再收到任何推送。
 */
export const connectPermissions = store.connect;

export function usePermissionState(): PermissionState {
  return useSyncExternalStore(store.subscribe, store.getState, store.getState);
}

/**
 * 是否拥有某个权限标识。
 *
 * <p>**未就绪时返回 `false`**：与其默认放行（漏权限）不如默认隐藏 ——
 * 隐藏多等一拍就会被下一次推送纠正，而放行一旦渲染出来就可能被用到。
 */
export function usePermission(code: string): boolean {
  const state = usePermissionState();
  return state.ready && state.permissions.includes(code);
}
