import { getHostGlobalState, isMicroApp, onHostGlobalStateChange } from 'common-tools/micro';

import { getStandaloneSession } from './index';

/**
 * 权限快照 —— 本应用"谁在登录、能做什么"的唯一来源。
 *
 * <h3>为什么是一个模块级的 store，而不是一个 Provider</h3>
 *
 * 与基座的 `session-sync` 同一个理由：权限不是某个组件的局部状态，
 * 而是**全应用共用**的事实（表格、按钮、将来的路由都要用）。
 * 做成 React context 也能用，但那样每个消费点都必须挂在 Provider 之下 ——
 * 而"挂在 Provider 之下"这件事一旦漏了，症状是权限恒为假、按钮永远不出现，
 * 排查方向会先指向"权限没下发"。模块级 store 没有这个前提条件。
 *
 * <h3>数据从哪来</h3>
 *
 * - 被基座加载：`HostGlobalState.permissions` / `roles`（**基座算好下发**，
 *   本应用不得自行调用 `/api/auth/me`，见 `docs/decisions/0018` 第 3 条）
 * - 独立运行：本应用自己的会话（同一份 session 实现）
 *
 * <h3>为什么区分 `ready`</h3>
 *
 * "没有权限"和"还不知道有没有权限"是两件事。前者该隐藏按钮，
 * 后者该等一等（默认放行会漏权限，默认隐藏会闪烁）。
 * `userId` 非空即表示基座已经给出可信的权限集合，因此 `ready` 由它决定 ——
 * 一个真正没有任何权限的已登录用户，`ready` 也是 `true`。
 */

export type PermissionState = {
  /** 权限集合是否可信（未登录时为 false，此时"没有权限"不代表真的没有） */
  ready: boolean;
  userId: string | null;
  roles: readonly string[];
  permissions: readonly string[];
};

/** 共用的空数组/空状态：引用稳定，`useSyncExternalStore` 依赖它避免无谓重渲染 */
const EMPTY: readonly string[] = [];
const EMPTY_STATE: PermissionState = {
  ready: false,
  userId: null,
  roles: EMPTY,
  permissions: EMPTY,
};

let state: PermissionState = EMPTY_STATE;

const listeners = new Set<() => void>();

export function getPermissionState(): PermissionState {
  return state;
}

export function subscribePermissions(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * 只在**内容真的变了**时通知。
 *
 * <p>比对的是引用而不是逐项内容：数组来自会话快照或基座下发的那一次推送，
 * 在两次变更之间是同一个引用，所以"引用相同 ⇒ 内容相同"成立，
 * 而且比逐项比对便宜。若这里改成每次发布都通知，
 * 基座每推一次全局状态（例如切主题）就会让所有消费组件重渲染一轮。
 */
function publish(next: PermissionState): void {
  if (
    next.ready === state.ready &&
    next.userId === state.userId &&
    next.roles === state.roles &&
    next.permissions === state.permissions
  ) {
    return;
  }
  state = next;
  for (const listener of Array.from(listeners)) listener();
}

function readFromHost(): PermissionState {
  const global = getHostGlobalState();
  const userId = global.userId ?? null;
  return {
    ready: userId !== null,
    userId,
    roles: global.roles ?? EMPTY,
    permissions: global.permissions ?? EMPTY,
  };
}

function readFromSession(): PermissionState {
  const snapshot = getStandaloneSession().getSnapshot();
  const user = snapshot.user;
  return {
    ready: Boolean(user),
    userId: user ? String(user.userId) : null,
    roles: user?.roles ?? EMPTY,
    permissions: user?.permissions ?? EMPTY,
  };
}

function reset(): void {
  publish(EMPTY_STATE);
}

/**
 * 接上权限来源，返回断开函数。
 *
 * <p>**必须在 `mount` 里调用、在 `unmount` 里断开**，不能写在模块顶层：
 * 子应用的模块在多次挂载之间是复用的，而基座桥在 `unbindHost` 时会清空全部订阅者 ——
 * 顶层订阅在第二次挂载后就不会再收到任何推送（症状是"第二次进入页面按钮全没了"）。
 */
export function connectPermissionSource(): () => void {
  if (isMicroApp()) {
    // fireImmediately：立刻推一次当前值，避免首帧停在"未就绪"而让按钮闪一下
    const stop = onHostGlobalStateChange(() => publish(readFromHost()), { fireImmediately: true });
    return () => {
      stop();
      reset();
    };
  }

  const session = getStandaloneSession();
  publish(readFromSession());
  const stop = session.subscribe(() => publish(readFromSession()));
  return () => {
    // 两个分支都要复位：留下上一轮的权限，会让下一次挂载在"尚未收到推送"的瞬间
    // 拿着已经失效的权限集合去渲染按钮
    stop();
    reset();
  };
}

/* ------------------------------------------------------------ 非 React 读取 */

/** 判定是否拥有某个权限标识。供非组件代码（例如发请求前的预检）使用 */
export function hasPermission(code: string): boolean {
  return state.ready && state.permissions.includes(code);
}
