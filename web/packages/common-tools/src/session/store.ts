import { logger as rootLogger } from '../logger';
import type { Logger } from '../logger';

import type {
  SessionListener,
  SessionSnapshot,
  SessionStorage,
  SessionStore,
  SessionTokens,
  SessionUser,
} from './types';

/** 持久化键名。带项目前缀，避免与同域其他应用撞车 */
const DEFAULT_STORAGE_KEY = 'bw.session';

/**
 * 持久化载荷版本。
 *
 * <p>**读不懂就丢弃**，不抛错：会话是可以重建的缓存，为一个过期格式把应用拦在
 * 白屏里毫无收益。版本号的作用是让"以后改结构"变成一个明确的判断，而不是碰运气解析。
 */
const STORAGE_VERSION = 1;

interface PersistedSession {
  v: number;
  refreshToken: string;
}

/** 内存存储。Node / SSR / 测试环境用，也用作真实存储不可用时的兜底 */
export function createMemoryStorage(): SessionStorage {
  const map = new Map<string, string>();
  return {
    getItem: (key) => map.get(key) ?? null,
    setItem: (key, value) => {
      map.set(key, value);
    },
    removeItem: (key) => {
      map.delete(key);
    },
  };
}

/**
 * 取默认存储。
 *
 * <p>访问 `globalThis.localStorage` **本身就可能抛错**（Safari 无痕模式下
 * 属性存在但一碰就抛 `SecurityError`），所以整个探测过程包在 try 里，
 * 拿不到就退化成内存存储 —— 表现为"刷新页面要重新登录"，而不是整个应用起不来。
 */
function resolveDefaultStorage(): SessionStorage {
  try {
    const storage = (globalThis as { localStorage?: SessionStorage }).localStorage;
    if (storage) return storage;
  } catch {
    // 落到内存存储
  }
  return createMemoryStorage();
}

const EMPTY_TOKENS: SessionTokens = {
  accessToken: null,
  refreshToken: null,
  expiresAt: null,
};

export interface SessionStoreConfig {
  /** 键值存储，默认 `localStorage`（不可用时退化为内存） */
  storage?: SessionStorage;
  /** 持久化键名，默认 `bw.session` */
  storageKey?: string;
  /** 日志器；传 `null` 关闭 */
  logger?: Logger | null;
}

/**
 * 创建会话存储。
 *
 * <p><b>令牌为什么分开存：</b>访问令牌**只放内存** —— 它的载荷里有用户与租户标识，
 * 落盘只是白白扩大暴露面；刷新令牌**持久化**，用来换取"刷新页面后静默续期"。
 * 这个权衡的兜底在后端：刷新令牌是轮换 + 重放检测的，一旦泄露并被使用，
 * 旧令牌的再次出现即撤销该用户全部会话（见 `docs/decisions/0018` 第 6 条）。
 *
 * <p><b>本模块不 import qiankun，也不 import 任何基座代码。</b>它只认"给我一份存储、
 * 我管一份会话" —— 这样同一份实现既能被基座驱动，也能被子应用单独驱动
 * （子应用单跑时提供方换成它自己的 store），这是 ADR-0018 第 2 条与第 8 条的落地要求。
 */
export function createSessionStore(config: SessionStoreConfig = {}): SessionStore {
  const storage = config.storage ?? resolveDefaultStorage();
  const storageKey = config.storageKey ?? DEFAULT_STORAGE_KEY;
  const log: Logger | null =
    config.logger === undefined ? rootLogger.child('session') : config.logger;

  const listeners = new Set<SessionListener>();

  let user: SessionUser | null = null;
  let tokens: SessionTokens = { ...EMPTY_TOKENS, refreshToken: readPersistedRefreshToken() };

  /*
   * 快照只在内容变化时重建。`useSyncExternalStore` 会在每次渲染时调用 `getSnapshot`，
   * 若每次返回新对象，React 会认为"一直在变"并陷入无限重渲染。
   */
  let snapshot: SessionSnapshot = buildSnapshot();

  function buildSnapshot(): SessionSnapshot {
    return {
      user,
      tokens,
      authenticated: Boolean(tokens.accessToken || tokens.refreshToken),
    };
  }

  /** 读取持久化的刷新令牌；读不懂一律当作没有 */
  function readPersistedRefreshToken(): string | null {
    let raw: string | null;
    try {
      raw = storage.getItem(storageKey);
    } catch {
      // 存储不可访问（无痕模式、配额为 0）——当作没有持久化会话即可
      return null;
    }
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as Partial<PersistedSession>;
      if (parsed.v !== STORAGE_VERSION || typeof parsed.refreshToken !== 'string') {
        log?.warn('持久化会话格式不认识，已忽略', { version: parsed.v });
        return null;
      }
      return parsed.refreshToken;
    } catch {
      log?.warn('持久化会话无法解析，已忽略');
      return null;
    }
  }

  function persistRefreshToken(token: string | null): void {
    try {
      if (token) {
        storage.setItem(storageKey, JSON.stringify({ v: STORAGE_VERSION, refreshToken: token }));
      } else {
        storage.removeItem(storageKey);
      }
    } catch {
      /*
       * 写不进去不影响本次会话（内存里是对的），只是刷新页面后要重新登录。
       * 为它抛错会把一次成功的登录变成失败，不划算。
       */
      log?.warn('会话无法持久化，刷新页面后需要重新登录');
    }
  }

  function commit(): void {
    const previous = snapshot;
    const next = buildSnapshot();
    /*
     * **只比引用，不做字段级比较。** 手写字段清单（`userId` / `roles` / `permissions`…）
     * 一旦漏了新增字段，更新会被静默丢弃 —— 表现为"权限改了但界面不变"，
     * 排查成本极高。相比之下，一次多余通知只是多一次重渲染。
     */
    if (
      previous.user === next.user &&
      previous.tokens === next.tokens &&
      previous.authenticated === next.authenticated
    ) {
      return;
    }
    snapshot = next;
    for (const listener of listeners) {
      try {
        listener(snapshot, previous);
      } catch (cause) {
        // 监听器自己的问题不该影响其他监听器，更不该影响会话本身
        log?.warn('会话监听器抛错，已忽略', {
          message: cause instanceof Error ? cause.message : String(cause),
        });
      }
    }
  }

  return {
    getSnapshot: () => snapshot,

    getAccessToken: () => tokens.accessToken,

    getRefreshToken: () => tokens.refreshToken,

    isAuthenticated: () => Boolean(tokens.accessToken || tokens.refreshToken),

    hasPermission: (permission) => user?.permissions.includes(permission) === true,

    set: (update) => {
      user = update.user;
      tokens = { ...tokens, ...update.tokens };
      if (update.tokens?.refreshToken !== undefined) persistRefreshToken(tokens.refreshToken);
      commit();
    },

    updateUser: (next) => {
      user = next;
      commit();
    },

    updateTokens: (partial) => {
      tokens = { ...tokens, ...partial };
      if (partial.refreshToken !== undefined) persistRefreshToken(tokens.refreshToken);
      commit();
    },

    clear: () => {
      user = null;
      tokens = { ...EMPTY_TOKENS };
      persistRefreshToken(null);
      commit();
    },

    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
