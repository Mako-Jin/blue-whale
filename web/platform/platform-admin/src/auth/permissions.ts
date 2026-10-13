import { createPermissionStore } from 'common-tools/micro';
import type { IdentitySnapshot } from 'common-tools/micro';

import { getStandaloneSession } from './index';

/**
 * 权限快照 —— 本应用"谁在登录、能做什么"的唯一来源。
 *
 * <h3>实现已经下沉，本文件只留"独立运行时的身份从哪来"</h3>
 *
 * store 本身（基座分支、会话分支、引用比对去抖、`ready` 三态、`connect`/断开）
 * 与另外四个子应用是**同一份实现**，在 `common-tools/micro` 的 `createPermissionStore`。
 * 本轮之前它在本应用与 `platform-audit-console` 里各有一份近似副本，而菜单与页面级
 * 过滤要接五个应用 —— 再抄三份就是五份。触发下沉的条件（第三个消费方）已经到了。
 *
 * <h3>为什么本应用不能直接用 `createConsoleAuth`</h3>
 *
 * 它的会话是**自己实现**的（`./index.ts` 的 `initializeAuth` +
 * `ensureStandaloneSession`），而不是 `createConsoleAuth` 那一套 ——
 * 后者是后出现的统一入口，本应用属于"现有两份本轮不动"的那一半。
 * 因此这里把它的会话适配成 {@link StandaloneIdentitySource} 的两个动作。
 *
 * <h3>数据从哪来</h3>
 *
 * - 被基座加载：`HostGlobalState.permissions` / `roles`（**基座算好下发**，
 *   本应用不得自行调用 `/api/auth/me`，见 `docs/decisions/0018` 第 3 条）
 * - 独立运行：本应用自己的会话（同一份 session 实现）
 *
 * <h3>为什么区分 `ready`</h3>
 *
 * "没有权限"和"还不知道有没有权限"是两件事。前者该隐藏入口，后者该等一等
 * （默认放行会漏权限，默认隐藏会闪烁）。`userId` 非空即表示权限集合可信 ——
 * 一个真正没有任何权限的已登录用户，`ready` 也是 `true`。
 */
const store = createPermissionStore({
  readStandaloneIdentity: (): IdentitySnapshot | null => {
    const user = getStandaloneSession().getSnapshot().user;
    if (!user) {
      return null;
    }
    return {
      userId: String(user.userId),
      roles: user.roles ?? [],
      permissions: user.permissions ?? [],
    };
  },
  onStandaloneIdentityChange: (listener) => getStandaloneSession().subscribe(() => listener()),
});

export const getPermissionState = store.getState;
export const subscribePermissions = store.subscribe;
export const connectPermissionSource = store.connect;
export const hasPermission = store.has;

export type { PermissionState } from 'common-tools/micro';
