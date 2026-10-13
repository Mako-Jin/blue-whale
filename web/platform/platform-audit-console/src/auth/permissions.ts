import { createPermissionStore } from 'common-tools/micro';

import { consoleAuth } from './index';

/**
 * 权限快照 —— 本应用"谁在登录、能做什么"的唯一来源。
 *
 * <h3>两边都是现成的：store 在 `common-tools/micro`，身份来源在 `consoleAuth`</h3>
 *
 * 快照本身（基座分支、会话分支、引用比对去抖、`ready` 三态、`connect` / 断开）
 * 与另外四个子应用是**同一份实现**（`createPermissionStore`）。
 * 身份来源也不再需要适配：`consoleAuth` 本身就是 `StandaloneIdentitySource` ——
 * 被基座加载时它的 `readStandaloneIdentity()` 返回 null，store 于是走基座分支，
 * 而不是去读一份**根本没被创建**的会话（那正是"两套会话并存"要避免的事）。
 *
 * 本轮之前这里包了一层把应用自己的会话掰成那两个动作的适配器；
 * 认证接线下沉后（见 `auth/index.ts`）它没有存在理由了，删掉。
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
 *
 * <h3>必须在 `mount` 里 `connect`、在 `unmount` 里断开</h3>
 *
 * 子应用的模块在多次挂载之间是复用的，而基座桥在 `unbindHost` 时会清空全部订阅者。
 * 另外 `connect` 会**读一次**身份，因此它必须排在 `bindHost` 之后（顺序理由见 `main.tsx`）。
 */
const store = createPermissionStore(consoleAuth);

export const getPermissionState = store.getState;
export const subscribePermissions = store.subscribe;
export const connectPermissionSource = store.connect;

export type { PermissionState } from 'common-tools/micro';
