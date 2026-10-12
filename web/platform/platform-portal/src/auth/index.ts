import { createAuthChannel } from 'common-api';
import type { AuthChannel } from 'common-api';
import { createSessionStore } from 'common-tools/session';
import type { SessionStore } from 'common-tools/session';

/**
 * 基座的会话与认证能力。
 *
 * <h3>为什么这里只有接线、没有登录逻辑</h3>
 *
 * 会话实现住在 `packages/`（`common-tools/session` 管存取、`common-api` 的
 * `createAuthChannel` 管刷新与失效），基座这一层**只提供一份会话存储、只做装配**。
 * 依据 `docs/decisions/0018` 第 2 条：子应用必须保留独立运行分支，登录逻辑若写死在基座，
 * 子应用单独起在 `:3200` 时就没有任何登录能力。
 *
 * <h3>为什么没有传 `onAuthLost`</h3>
 *
 * `onAuthLost` 的语义是"会话彻底没救了，跳登录页"。基座用的是**声明式**做法：
 * 会话清空 → `RequireAuth` 订阅到变化 → `<Navigate to="/login">`。
 * 命令式跳转与声明式重定向同时存在时，两者会在 render 阶段打架
 * （一边改 history、一边又让 URL 不匹配）。所以这里只留一份机制。
 */

/** 唯一的会话存储 */
export const session: SessionStore = createSessionStore();

/**
 * 契约路径自带 `/api` 前缀，因此 **`baseUrl` 留空**；开发期由 vite 的 `server.proxy`
 * 把 `/api` 转发到 `:8080`，生产期由网关同域路由。
 * @see ../../vite.config.ts 的 `server.proxy`
 */
export const auth: AuthChannel = createAuthChannel({ session });

/** 启动时的会话恢复阶段 */
export type RestorePhase =
  /** 尚未开始（正常情况下只在模块刚加载的瞬间出现） */
  | 'idle'
  /** 正在用持久化的刷新令牌续期 */
  | 'restoring'
  /** 已结束：要么恢复成功，要么确认没有可恢复的会话 */
  | 'done';

let phase: RestorePhase = 'idle';

const phaseListeners = new Set<() => void>();

let restoreTask: Promise<void> | null = null;

function setPhase(next: RestorePhase): void {
  if (next === phase) return;
  phase = next;
  // 先复制再遍历：监听器在回调里退订（React 卸载时常见）不影响本次分发
  for (const listener of Array.from(phaseListeners)) listener();
}

export function getRestorePhase(): RestorePhase {
  return phase;
}

export function subscribeRestorePhase(listener: () => void): () => void {
  phaseListeners.add(listener);
  return () => {
    phaseListeners.delete(listener);
  };
}

/**
 * 启动时尝试恢复会话（**幂等**：重复调用共用同一个 promise）。
 *
 * <p>为什么不 `await` 后再渲染：恢复要走一次网络（刷新接口），把首屏押在它身上
 * 就违反了 `docs/decisions/0003` 的"首屏零阻塞"。界面先渲染出来并显示
 * "正在恢复会话"，恢复完再决定放行还是跳登录页。
 *
 * <p>**必须有这一步**：权限不在访问令牌里（`JwtCodec` 的载荷只有用户与租户标识），
 * 所以仅凭内存里的令牌无法知道"我是谁、我有哪些权限"，只能走一次刷新接口拿回来。
 */
export function ensureSessionRestored(): Promise<void> {
  if (restoreTask) return restoreTask;

  setPhase('restoring');
  restoreTask = auth.restore().then(
    () => {
      setPhase('done');
    },
    () => {
      // `restore()` 自身已把失败吞成 `null` 并清空会话；这里只是保证阶段一定推进，
      // 否则界面会永远停在"正在恢复会话"，比跳登录页糟得多。
      setPhase('done');
    },
  );
  return restoreTask;
}

/** 仅供测试使用：把模块状态复位，避免用例之间互相污染 */
export function resetAuthForTest(): void {
  phase = 'idle';
  phaseListeners.clear();
  restoreTask = null;
}
