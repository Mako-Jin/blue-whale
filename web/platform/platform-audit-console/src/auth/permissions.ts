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
 * 本轮之前它在本应用与 `platform-admin` 里各有一份近似副本，而菜单与页面级过滤
 * 要接五个应用 —— 再抄三份就是五份。触发下沉的条件（第三个消费方）已经到了。
 *
 * <h3>本应用只把权限用在"看不看得到区块"上</h3>
 *
 * 特别地：**演练区的按钮不做权限显隐**（见 `audit/DrillPanel.tsx`）——
 * "越权尝试"本身就是要被记录下来的事件，把按钮藏起来等于把最值得看的记录抹掉。
 * 本 store 因此只被用来过滤"流水 / 聚合 / 演练区"这三块的可见性，
 * 不参与任何一个按钮的判定。
 *
 * <h3>数据从哪来</h3>
 *
 * - 被基座加载：`HostGlobalState.permissions` / `roles`（**基座算好下发**，
 *   本应用不得自行调用 `/api/auth/me`，见 `docs/decisions/0018` 第 3 条）
 * - 独立运行：本应用自己的会话（同一份 session 实现）
 *
 * <h3>为什么区分 `ready`</h3>
 *
 * "没有权限"和"还不知道有没有权限"是两件事。前者该隐藏区块，后者该等一等。
 * `userId` 非空即表示权限集合可信，因此 `ready` 由它决定 ——
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

export type { PermissionState } from 'common-tools/micro';
