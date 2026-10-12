import { createHttpClient } from 'common-tools/http';
import type { HttpConfig } from 'common-tools/http';
import { logger as rootLogger } from 'common-tools/logger';
import type { SessionStore, SessionUser } from 'common-tools/session';

import { createAccessApi } from './access';
import type { AccessApi } from './access';
import { createAuthApi, toSessionTokens, toSessionUser } from './auth';
import type { AuthApi, AuthLoginRequest } from './auth';
import { createEventApi } from './events';
import type { EventApi } from './events';
import { createLogApi } from './log';
import type { LogApi } from './log';
import { createSystemApi } from './system';
import type { SystemApi } from './system';

/** 会话通道里的接口集合（与 `createApiClient` 一致，只是 auth 已在其中） */
export interface ChannelApi {
  /** 底层请求客户端（已接好令牌注入与 401 恢复） */
  readonly http: ReturnType<typeof createHttpClient>;
  readonly auth: AuthApi;
  readonly access: AccessApi;
  readonly log: LogApi;
  readonly event: EventApi;
  readonly system: SystemApi;
}

export interface AuthChannelConfig extends HttpConfig {
  /** 会话存储 */
  session: SessionStore;
  /**
   * 会话彻底失效时的界面动作（跳登录页）。
   *
   * <p>不注入也能工作：会话照样被清空，只是界面不会自动跳走 ——
   * 传输内核与本层都不碰路由，跳转是应用的决定。
   */
  onAuthLost?: () => void;
}

/** 会话通道：把"契约 + 会话存储 + 传输钩子"接成一个可用的认证能力 */
export interface AuthChannel {
  /** 全部业务接口 */
  readonly api: ChannelApi;
  /** 会话存储 */
  readonly session: SessionStore;
  /** 登录并建立会话 */
  login(input: AuthLoginRequest): Promise<SessionUser>;
  /** 登出：先撤销服务端刷新令牌，**无论成败都清空本地会话** */
  logout(): Promise<void>;
  /** 用持久化的刷新令牌恢复会话；恢复不了返回 `null` 并清空本地会话 */
  restore(): Promise<SessionUser | null>;
  /** 用现有访问令牌重新拉取身份与权限（后台改了权限之后刷新用） */
  reload(): Promise<SessionUser | null>;
  /**
   * 尝试恢复会话，返回"是否已恢复"。
   *
   * <p>它是 `common-tools/http` 的 `onUnauthorized` 钩子所调用的那一环：
   * 内核只负责"问一句还能不能救"，**并发去重由内核保证**（single-flight）——
   * 因为刷新令牌是轮换的，并发的第二次刷新会被服务端判成重放攻击并撤销该用户全部会话。
   *
   * <p>**本层再合并一次**，原因是内核的去重是"按客户端"的：它只能合并**同一个**
   * `HttpClient` 上的并发 401。而当同一个通道被多处使用（基座自己的请求、
   * 经 `HostAuthBridge` 委托过来的若干子应用）时，就会出现多个客户端同时调用本方法，
   * 内核那一层看不见彼此 —— 于是又变成并发刷新。刷新是**会话级**动作，
   * 合并也必须发生在会话级（本方法）才算完整。
   */
  reauthenticate(): Promise<boolean>;
}

/**
 * 创建会话通道。
 *
 * <p><b>这是前端消费权限域的入口</b>：它把三方接在一起 —— 契约（`./auth` 的认证接口）、
 * 会话存储（`common-tools/session`）、传输钩子（`common-tools/http` 的令牌注入与 401 恢复）。
 * 应用侧只需"给它一份会话存储、告诉它登录态没了怎么跳"，不必各自重写一遍接线 ——
 * 那正是 ADR-0018 要避免的"某处只判了登录、忘了判权限"。
 *
 * <p>典型用法（基座与单跑的子应用是同一段代码，差别只在谁提供会话存储）：
 * ```ts
 * // 契约路径自带 `/api` 前缀，所以 baseUrl 留空（开发期由 vite 代理转发）
 * const session = createSessionStore();
 * const auth = createAuthChannel({ session, onAuthLost: () => gotoLogin() });
 *
 * await auth.restore();                     // 启动时用持久化的刷新令牌续期
 * await auth.login({ tenantCode: 'default', username: 'admin', password: '...' });
 * await auth.api.log.page({ level: 'error' });   // 令牌注入与 401 恢复对调用方透明
 * ```
 */
export function createAuthChannel(config: AuthChannelConfig): AuthChannel {
  const { session, onAuthLost, ...httpConfig } = config;
  const log = rootLogger.child('auth');

  /*
   * 打破"钩子要引用通道自身"的循环：内核在请求发生时才回调钩子，
   * 那时 `channel` 早已赋值完成。若直接在 `createHttpClient` 的参数里引用 `channel`，
   * 会踩到 TDZ（`Cannot access before initialization`）。
   */
  let channel: AuthChannel | null = null;

  const http = createHttpClient({
    ...httpConfig,
    accessTokenProvider: () => session.getAccessToken(),
    onUnauthorized: () => channel?.reauthenticate() ?? false,
    /*
     * 内核判定"带着令牌仍 401，且恢复已无意义"（刷新失败，或刷新后重试仍 401）时调用。
     * 清会话放在这里而不散在各调用点：会话失效是全局事实，只该有一个清空入口。
     */
    onAuthLost: () => {
      log.warn('会话已失效，清空本地会话');
      session.clear();
      onAuthLost?.();
    },
  });

  const authApi = createAuthApi(http);

  /**
   * 会话级刷新去重（single-flight）。
   *
   * <p>内核对同一个客户端上的并发 401 已做合并，但它看不见**跨客户端**的并发：
   * 基座自己发一个请求、同时有两个子应用经 `HostAuthBridge` 委托过来，
   * 就是三个客户端同时要求刷新。刷新令牌只认一次，其余两次会被判成重放 ——
   * 代价是撤销该用户全部会话（`docs/decisions/0018` 第 7 条）。
   * 因此合并点必须落在"会话"这一层，也就是下面这个方法。
   */
  let pendingRefresh: Promise<boolean> | null = null;

  async function refreshOnce(): Promise<boolean> {
    const refreshToken = session.getRefreshToken();
    // 没有刷新令牌就没什么可救的 —— 直接返回，避免白跑一次请求
    if (!refreshToken) return false;
    try {
      const result = await authApi.refresh({ refreshToken });
      session.set({ user: toSessionUser(result), tokens: toSessionTokens(result) });
      return true;
    } catch (cause) {
      log.warn('刷新令牌失败，会话无法恢复', {
        message: cause instanceof Error ? cause.message : String(cause),
      });
      return false;
    }
  }

  const instance: AuthChannel = {
    api: {
      http,
      auth: authApi,
      access: createAccessApi(http),
      log: createLogApi(http),
      event: createEventApi(http),
      system: createSystemApi(http),
    },
    session,

    reauthenticate: () => {
      if (!pendingRefresh) {
        // 与内核同一写法：先同步起跑，再用 `finally` 清位；
        // 若把清位写在 `then` 里，一次失败会让后续调用永远拿到那个已 settle 的 Promise
        pendingRefresh = refreshOnce().finally(() => {
          pendingRefresh = null;
        });
      }
      return pendingRefresh;
    },

    login: async (input) => {
      const result = await authApi.login(input);
      const user = toSessionUser(result);
      session.set({ user, tokens: toSessionTokens(result) });
      return user;
    },

    logout: async () => {
      const refreshToken = session.getRefreshToken();
      try {
        if (refreshToken) await authApi.logout({ refreshToken });
      } catch (cause) {
        /*
         * 撤销失败不影响"用户要登出"这个意图 —— 本地不清干净的后果更糟：
         * 界面还显示着登录态，令牌却已经不可用。只记一条 warn。
         */
        log.warn('撤销刷新令牌失败，本地会话仍将清除', {
          message: cause instanceof Error ? cause.message : String(cause),
        });
      } finally {
        session.clear();
      }
    },

    restore: async () => {
      if (!session.getRefreshToken()) return null;
      const recovered = await instance.reauthenticate();
      if (!recovered) {
        session.clear();
        return null;
      }
      return session.getSnapshot().user;
    },

    reload: async () => {
      const me = await authApi.me();
      const user = toSessionUser(me);
      session.updateUser(user);
      return user;
    },
  };

  channel = instance;
  return instance;
}
