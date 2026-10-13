import { createHttpClient, isHttpError } from 'common-tools/http';
import type { HttpConfig } from 'common-tools/http';
import { logger as rootLogger } from 'common-tools/logger';
import type { SessionStore, SessionUser } from 'common-tools/session';

import { createAccessApi } from './access';
import type { AccessApi } from './access';
import { createAuditApi } from './audit';
import type { AuditApi } from './audit';
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
  /** 审计域：**只有读**，写记录由后端拦截器负责（见 `./audit` 的文件头） */
  readonly audit: AuditApi;
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
   * 本人修改口令。**成功后本地会话随之结束**（原因见实现处），因此界面不要
   * 在 `await` 之后写"成功提示" —— 那时页面已经在去登录页的路上了。
   *
   * <p>两个口令用**位置参数**而不是一个对象：它们同为字符串，写成
   * `{ oldPassword: x, newPassword: y }` 时把两者写反，类型检查一声不响，
   * 而症状是"用新口令去证明自己是本人"—— 一次必然失败、且看起来像口令错了。
   */
  changePassword(oldPassword: string, newPassword: string): Promise<void>;
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
  /**
   * 取走"上一次会话被结束的原因"，**取一次即清空**。
   *
   * <h3>为什么需要它</h3>
   *
   * 会话失效有两种来路，用户感受到的却完全不同：
   * <ul>
   *   <li><b>自己点登出</b> —— 他知道发生了什么，不需要解释。</li>
   *   <li><b>被服务端拒绝续期</b> —— 他正在做事，页面忽然回到登录页。此时
   *       "为什么"只存在于服务端那句响应里（"口令已过期，请联系管理员重置口令"、
   *       "会话已失效，请重新登录"…），而这条路径**没有任何用户动作**，
   *       界面自己讲不出原因。</li>
   * </ul>
   * 这个值是给第二种情况用的：它让登录页能开口说第一句话，而不是让用户对着一个
   * 空白表单猜自己是不是点错了什么。
   *
   * <h3>为什么它不在会话存储里</h3>
   *
   * 它出现的那一刻，会话正被 `clear()` 清掉 —— 放在会话存储里会被同一句清空抹掉。
   * 它也不是"会话状态"，而是"刚刚发生了什么"的**一次性事件**。
   *
   * <h3>为什么取一次就清空</h3>
   *
   * 它只对紧接着的那一次登录页渲染有意义。留着的话，用户换台设备、隔一天再打开，
   * 迎面看到的会是一句昨天的原因 —— 而昨天的那次会话早已无关。
   */
  consumeAuthNotice(): string | null;
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

  /** 上一次"被服务端拒绝续期"时服务端说的那句话。见 `consumeAuthNotice` */
  let authNotice: string | null = null;

  async function refreshOnce(): Promise<boolean> {
    const refreshToken = session.getRefreshToken();
    // 没有刷新令牌就没什么可救的 —— 直接返回，避免白跑一次请求
    if (!refreshToken) return false;
    try {
      const result = await authApi.refresh({ refreshToken });
      session.set({ user: toSessionUser(result), tokens: toSessionTokens(result) });
      return true;
    } catch (cause) {
      /*
       * 只记**服务端明确拒绝**时的那句话，不记传输层的失败。
       *
       * 判据是"谁有权说这是会话的错"：业务失败意味着服务端收到了这次刷新、
       * 看清了它、并给出一个结论 —— 那句话就是用户被请出去的原因，原样转达即可
       * （前端另写一套说法只会变成第二份会过期的文案）。
       * 而断网、超时不是"会话失效"的证据：可能是本地网络抖了一下，
       * 把它当成原因展示，用户会以为必须重新登录。
       */
      if (isHttpError(cause) && cause.kind === 'business') {
        authNotice = cause.message;
      }
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
      audit: createAuditApi(http),
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
        /*
         * 启动时这次恢复失败**不算"被请出去"** —— 用户还没进来过，没有"被中断"这回事。
         * 不清掉的话，隔一周再打开应用会迎面看到一句"会话已失效"：话是对的，
         * 但它解释的是一件用户没有经历过的事。
         */
        authNotice = null;
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

    changePassword: async (oldPassword, newPassword) => {
      await authApi.changePassword({ oldPassword, newPassword });

      /*
       * 成功之后**本地会话必然结束**，而且必须在这里结束。
       *
       * 服务端改密时撤销的是该账号的**全部**刷新令牌，包括本次会话手上这一个
       * （它认不出"当前用的是哪一个"—— 本请求带的是访问令牌，里面没有刷新令牌的 jti）。
       * 因此这次会话已经无法续期：访问令牌一到期（默认 2 小时）就是一次"突然掉登录"，
       * 而那时用户早已忘了自己刚改过口令。把它推迟到那一刻，只会换来一张查不出原因的
       * 故障单；当场结束，则正好让用户用新口令验证一次"新口令真的能用"。
       *
       * 判据：这不是界面的选择，而是服务端行为的**必然结果**。放在通道里，
       * 它就只有一个实现；散在各个界面里，它就只是一句"记得清会话"。
       */
      log.info('口令已修改，本地会话随之结束（服务端已撤销全部刷新令牌，本次会话无法续期）');
      session.clear();
    },

    consumeAuthNotice: () => {
      const notice = authNotice;
      authNotice = null;
      return notice;
    },
  };

  channel = instance;
  return instance;
}
