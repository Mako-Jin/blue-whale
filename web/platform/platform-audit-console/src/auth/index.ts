import { createAuthChannel } from 'common-api';
import type { AuthChannel } from 'common-api';
import { createLogger } from 'common-tools';
import { getHostProps } from 'common-tools/micro';
import { createSessionStore } from 'common-tools/session';
import type { SessionStore } from 'common-tools/session';

/**
 * 本应用"从哪取令牌"的唯一答案 —— 与 `platform-admin` 同构，理由也相同。
 *
 * <h3>为什么这里没有登录页</h3>
 *
 * 依据 `docs/decisions/0018` 第 8 条：子应用单跑时走**同一份会话实现**，
 * 但不得再写一个登录页。登录逻辑一旦复制出第二份，就会出现两套跳转、
 * 两套错误提示、两套会话清理。独立运行时的起步方式因此是"用开发账号自动登录"
 * （见 `ensureStandaloneSession`），复用的仍是 `common-api` 的 `createAuthChannel`。
 *
 * <h3>审计台比权限管理台更需要它</h3>
 *
 * 本应用**一个写接口都不自己发**（审计只有 GET），但每个 GET 都要带令牌 ——
 * 换句话说，它 100% 的请求都是"已鉴权请求"，认证没接好就一屏 403/401。
 *
 * <h3>与 platform-admin 的关系</h3>
 *
 * 两个管理型子应用各持一份这层接线，**互不引用**（`platform-*` 之间不得互相依赖）。
 * 这层代码在两个应用里逐字相同，是**有意留着的重复**：
 * 现在下沉到 `packages/` 需要同时改动 `platform-admin` 的既有测试与端到端脚本，
 * 风险与"审计管道"这件事无关。触发条件是"出现第三个管理型子应用"——
 * 届时它该变成 `packages/common-*` 里的一份。
 */

const logger = createLogger('auth:audit');

/**
 * 应用侧的认证端口。
 *
 * <p>刻意只有两个动作：取令牌、要一次刷新。**没有"清空会话"** ——
 * 会话失效是全局事实，清空只该有它的持有者一个入口（基座或本应用的通道）。
 */
export type AuthPort = {
  getAccessToken(): string | null;
  reauthenticate(): Promise<boolean>;
};

/** 独立运行时的会话存储与通道（被基座加载时不会创建） */
let standaloneSession: SessionStore | null = null;
let standaloneChannel: AuthChannel | null = null;

function ensureStandaloneChannel(): AuthChannel {
  if (!standaloneChannel) {
    standaloneSession = createSessionStore();
    standaloneChannel = createAuthChannel({ session: standaloneSession });
  }
  return standaloneChannel;
}

/** 独立运行时的会话存储（权限来源也取自它） */
export function getStandaloneSession(): SessionStore {
  ensureStandaloneChannel();
  // ensureStandaloneChannel 一定会把它建出来
  return standaloneSession as SessionStore;
}

function resolvePort(): AuthPort {
  const bridge = getHostProps()?.auth;
  if (bridge) {
    logger.info('使用基座下发的认证能力（令牌按需取、刷新由基座合并）');
    return {
      getAccessToken: () => bridge.getAccessToken(),
      reauthenticate: () => bridge.reauthenticate(),
    };
  }

  logger.warn('未检测到基座认证能力，改用应用自身的会话（独立运行模式）');
  const channel = ensureStandaloneChannel();
  return {
    getAccessToken: () => channel.session.getAccessToken(),
    reauthenticate: () => channel.reauthenticate(),
  };
}

let port: AuthPort | null = null;

/**
 * 确定认证来源。
 *
 * <p>**必须在 mount 之后调用**（被基座加载时，`bindHost` 之前拿不到 props，
 * 那时会把"被基座加载"误判成"独立运行"）。调用点集中在 `main.tsx`。
 */
export function initializeAuth(): AuthPort {
  port = resolvePort();
  return port;
}

/** 取已确定的认证端口。未初始化属于编程错误，直接抛而不是悄悄降级 */
export function getAuthPort(): AuthPort {
  if (!port) {
    throw new Error('认证端口尚未初始化：应在 mount 时先调用 initializeAuth()');
  }
  return port;
}

/**
 * 独立运行时的开发账号（可用环境变量覆盖，默认指向 h2 profile 的种子管理员）。
 *
 * <p><b>只在开发构建里构造</b>：生产构建下条件为常量假，被折叠成 `undefined`，
 * 整个对象字面量随之被摇掉 —— 口令不会进产物（见 `docs/decisions/0033`，
 * 由 `scripts/verify-bundle-secrets.mjs` 盯着）。**`undefined` 即"不自动登录"**。
 */
const devAccount = import.meta.env.DEV
  ? {
      tenantCode: import.meta.env.VITE_DEV_TENANT_CODE ?? 'default',
      username: import.meta.env.VITE_DEV_USERNAME ?? 'admin',
      password: import.meta.env.VITE_DEV_PASSWORD ?? 'Admin@12345',
    }
  : undefined;

/**
 * 独立运行时确保有一份可用会话。
 *
 * <p>先尝试用持久化的刷新令牌续期（**一次刷新即恢复身份与权限**，因为返回体自带
 * `roles` / `permissions`），再退回开发账号登录。
 *
 * <p>开发账号**只在开发构建里存在**：生产构建若被独立部署，本函数只记一条错误日志，
 * 不会用默认口令去登录任何东西。
 */
export async function ensureStandaloneSession(): Promise<void> {
  const channel = ensureStandaloneChannel();
  const { session } = channel;

  if (!session.isAuthenticated() && session.getRefreshToken()) {
    await channel.restore();
  }
  if (session.isAuthenticated()) {
    logger.info('独立运行：会话已就绪');
    return;
  }

  if (!devAccount) {
    // 走到这里只可能是生产构建：开发构建下上面的三元必然给出一个账号。
    logger.error('独立运行且没有可用会话：本应用不含登录页（ADR-0018 第 8 条），请改由基座加载');
    return;
  }

  logger.warn('独立运行：用开发账号自动登录', { username: devAccount.username });
  await channel.login(devAccount);
}
