import { createLogger } from 'common-tools';
import { getHostProps } from 'common-tools/micro';
import type { IdentitySnapshot, StandaloneIdentitySource } from 'common-tools/micro';
import { createSessionStore } from 'common-tools/session';
import type { SessionStore } from 'common-tools/session';

import { createAuthChannel } from './channel';
import type { AuthChannel } from './channel';

/**
 * 子应用的认证端口 —— "从哪取令牌"的唯一答案。
 *
 * <p>刻意只有两个动作：取令牌、要一次刷新。**没有"清空会话"** ——
 * 会话失效是全局事实，清空只该有它的持有者一个入口（基座，或本模块自建的那条通道）。
 *
 * <p>形状与 `HostAuthBridge`（`common-tools/micro`）一致，
 * 因此"用基座的"与"用自建的"对调用方是同一种东西。
 */
export type ConsoleAuthPort = {
  getAccessToken(): string | null;
  reauthenticate(): Promise<boolean>;
};

export interface ConsoleAuthOptions {
  /** 应用名，只用于日志前缀，便于在控制台分清是哪个应用在说话 */
  name: string;
  /**
   * 是否允许用开发账号自动登录。
   *
   * <p>由**调用方**传 `import.meta.env.DEV`。本包刻意不自己读 `import.meta.env`：
   * 那会把 Vite 的环境变量体系带进一个与框架无关的契约包，
   * 也让"生产构建绝不自动登录"这件事藏在一个看不见的全局变量里。
   * 放在调用点上，评审时一眼能看到它是怎么来的。
   */
  allowDevAutoLogin: boolean;
  /** 独立运行时的开发账号。仅在 {@link allowDevAutoLogin} 为 true 时会被使用 */
  devAccount: {
    tenantCode: string;
    username: string;
    password: string;
  };
}

export interface ConsoleAuth extends StandaloneIdentitySource {
  /** 给 {@link createApiClient} 用的令牌端口 */
  readonly port: ConsoleAuthPort;
  /**
   * 确保独立运行时有一份可用会话。
   *
   * <p>**必须在 mount 之后调用**（被基座加载时，`bindHost` 之前拿不到 props，
   * 那时会把"被基座加载"误判成"独立运行"）；在基座内调用它是安全的空操作。
   */
  ensureSession(): Promise<void>;
  /**
   * 独立运行时自建的会话；**被基座加载时返回 null**（那时会话在基座手里）。
   *
   * <p>存在的理由有两个，都是"必须有"的：
   * <ul>
   *   <li>权限来源要订阅它（见 {@link ConsoleAuth.onStandaloneIdentityChange}）；</li>
   *   <li>测试要能在不绑基座的前提下摆好一个身份 —— 整棵权限链路的起点就是
   *       "会话里有哪些权限"，没有它就只能靠绑一个假基座来测，
   *       而那会把被测对象从"读会话"换成"读基座"，测的就不是同一件事了。</li>
   * </ul>
   *
   * <p>它**会按需创建**会话（与 `getAccessToken` 同一条路径）。因此不要在
   * "可能被基座加载"的时刻无条件调用它 —— 那会造出第二套会话，
   * 正是本模块开头要避免的事。判据是返回值是不是 null。
   */
  getStandaloneSession(): SessionStore | null;
}

/**
 * 创建子应用的认证端口。
 *
 * <h3>为什么这个函数住在 `common-api` 而不是 `common-tools`</h3>
 *
 * 它需要 {@link createAuthChannel}，而通道在 `common-api`；若把它放进 `common-tools`，
 * 后者就得反向依赖 `common-api`（而 `common-api` 依赖 `common-tools`）—— 直接成环。
 * 放在这一层的另一个好处是：它把"契约 + 会话 + 传输钩子"三样都已在手的东西接起来，
 * 不必再引入任何新的依赖方向。
 *
 * <h3>为什么要有它（而不是每个子应用各写一份）</h3>
 *
 * `platform-admin` 与 `platform-audit-console` 各自持有一份这层接线的副本，
 * 当时是**有意留着的重复**（触发下沉的条件写在 `web/README.md`：出现第三个管理型子应用）。
 * 日志台与行为事件台是第三、第四个消费方 —— 再复制两份，等于把一份已经确认要统一的东西
 * 又抄了两遍，之后还要改四处。因此新增的消费方一律用本函数；
 * 现有两份**本轮不动**（下沉要连带改动它们的既有测试与端到端脚本，是独立的一次重构）。
 *
 * <h3>典型用法</h3>
 *
 * ```ts
 * const auth = createConsoleAuth({ name: 'log-console', devAccount: {...} });
 *
 * // 令牌在**请求时**求值，所以这段可以在 mount 之前就装配好
 * const api = createApiClient({
 *   accessTokenProvider: () => auth.port.getAccessToken(),
 *   onUnauthorized: () => auth.port.reauthenticate(),
 * });
 *
 * // 独立运行分支：mount 之后
 * await auth.ensureSession();
 * ```
 */
export function createConsoleAuth(options: ConsoleAuthOptions): ConsoleAuth {
  const logger = createLogger(`auth:${options.name}`);

  /** 自建会话（仅独立运行时才会创建；被基座加载时不创建，避免两套会话并存） */
  let standaloneSession: SessionStore | null = null;
  let standaloneChannel: AuthChannel | null = null;

  const ensureStandaloneChannel = (): AuthChannel => {
    if (!standaloneChannel) {
      standaloneSession = createSessionStore();
      standaloneChannel = createAuthChannel({ session: standaloneSession });
    }
    return standaloneChannel;
  };

  /**
   * 独立运行时自建的会话；被基座加载时返回 null。
   *
   * <p>判据是"有没有宿主桥"而不是"有没有会话"，与 {@link ConsoleAuthPort.getAccessToken}
   * 同一理由：用后者会把"还没登录"误判成"独立运行"，进而自建起第二套会话。
   */
  const getStandaloneSession = (): SessionStore | null =>
    getHostProps()?.auth ? null : ensureStandaloneChannel().session;

  const port: ConsoleAuthPort = {
    getAccessToken: () => {
      const bridge = getHostProps()?.auth;
      if (bridge) {
        return bridge.getAccessToken();
      }
      // 未挂载在基座下：只有这时才碰自建会话。
      // 用"有没有宿主桥"而不是"有没有会话"来判断，是因为后者的答案是"还没有"，
      // 会把"还没登录"误判成"独立运行"，进而自建起第二套会话。
      return ensureStandaloneChannel().session.getAccessToken();
    },
    reauthenticate: async () => {
      const bridge = getHostProps()?.auth;
      if (bridge) {
        return bridge.reauthenticate();
      }
      return ensureStandaloneChannel().reauthenticate();
    },
  };

  const ensureSession = async (): Promise<void> => {
    if (getHostProps()?.auth) {
      // 被基座加载：会话由基座统一管，本模块一个字节都不碰
      logger.info('使用基座下发的认证能力（令牌按需取、刷新由基座合并）');
      return;
    }

    logger.warn('未检测到基座认证能力，改用应用自身的会话（独立运行模式）');
    const channel = ensureStandaloneChannel();
    const { session } = channel;

    // 先用持久化的刷新令牌续期：**一次刷新即恢复身份**，比重新登录少一次口令校验
    if (!session.isAuthenticated() && session.getRefreshToken()) {
      await channel.restore();
    }
    if (session.isAuthenticated()) {
      logger.info('独立运行：会话已就绪');
      return;
    }

    if (!options.allowDevAutoLogin) {
      // 生产构建被独立部署：**不登录**。此处只记错误，不抛 ——
      // 抛出去会让整个子应用白屏，而"没有会话"只影响需要鉴权的面板。
      logger.error('独立运行且没有可用会话：本应用不含登录页（ADR-0018 第 8 条），请改由基座加载');
      return;
    }

    logger.warn('独立运行：用开发账号自动登录', { username: options.devAccount.username });
    await channel.login(options.devAccount);
  };

  /**
   * 独立运行时的身份快照 —— 供 {@code createPermissionStore} 做页面级过滤用。
   *
   * <p><b>被基座加载时返回 null</b>：那时身份的来源是基座下发的全局状态
   * （`HostGlobalState.permissions`），本应用自己的会话根本没被创建
   * （这正是"避免两套会话并存"那条）。返回 null 让权限 store 干脆走基座分支，
   * 而不是去读一份永远为空的会话。
   *
   * <p>用"有没有宿主桥"而不是"有没有会话"来区分两种运行方式，与
   * {@link ConsoleAuthPort.getAccessToken} 同一判据 —— 用后者会把
   * "还没登录"误判成"独立运行"。
   */
  const readStandaloneIdentity = (): IdentitySnapshot | null => {
    const user = getStandaloneSession()?.getSnapshot().user;
    if (!user) {
      return null;
    }
    return {
      userId: String(user.userId),
      roles: user.roles ?? [],
      permissions: user.permissions ?? [],
    };
  };

  /**
   * 订阅独立运行时的身份变更。
   *
   * <p>**订阅会顺手把会话建出来**（`getStandaloneSession` 按需创建）：调用点的顺序是
   * "先接权限来源、再 `ensureSession`"（见各子应用 `main.tsx` 的独立运行分支），
   * 若这里只订阅"可能还不存在的会话"，独立运行时的那次身份变更会被漏掉 ——
   * 症状是"单跑时入口一个都不出现，刷新一下才正常"。
   */
  const onStandaloneIdentityChange = (listener: () => void): (() => void) => {
    const session = getStandaloneSession();
    if (!session) {
      // 被基座加载：身份由基座下发，本应用这条通道永远不会响。
      // 返回一个空函数而不是抛错 —— 调用方（权限 store）不关心自己在哪个分支
      return () => {};
    }
    return session.subscribe(() => listener());
  };

  return {
    port,
    ensureSession,
    getStandaloneSession,
    readStandaloneIdentity,
    onStandaloneIdentityChange,
  };
}
