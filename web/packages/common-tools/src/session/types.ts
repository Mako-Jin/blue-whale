/**
 * 会话相关类型。
 *
 * <p><b>为什么这里的用户信息用"结构化描述"而不是 `common-api` 的 `MeVO`：</b>
 * 本包不依赖契约包（依赖方向是 `common-api` → `common-tools`），而且会话本身
 * 也不需要知道字段是从哪个接口来的。只要**形状**对得上，谁来喂都行 ——
 * 与 `micro/types.ts` 里 `HostGlobalState` 的做法同一个理由。
 */

/** 会话中的用户信息。只保留权限判定与展示真正会用到的字段 */
export interface SessionUser {
  userId: number | string;
  username: string;
  displayName?: string;
  tenantId?: number | string;
  tenantCode?: string;
  /** 角色编码集合，如 `ADMIN` */
  roles: string[];
  /** 权限（资源标识）集合，如 `auth:user:create`。**标识由后端定义，前端不得拼接自造** */
  permissions: string[];
  /**
   * 是否还在等本人改口令（后端 `mustChangePassword` 为 1 时）。
   *
   * <p><b>为什么是必填的布尔值，而不是可选的、或者后端那个 0/1：</b>它和 `roles` /
   * `permissions` 一样是**判定**用的字段（引导弹窗弹不弹全看它），不是展示字段。
   * 判定字段留下"缺席"这种第三态，后面一定要有人为它猜一个默认值 ——
   * 而无论猜"否"（该弹不弹，静默失效）还是猜"是"（对着没这回事的人弹窗）
   * 都是错的一方。转换只发生在 `common-api` 的 `toSessionUser` 一处，
   * 因此这里固定成 `boolean` 不会给任何调用点添负担。
   */
  mustChangePassword: boolean;
}

/** 会话令牌 */
export interface SessionTokens {
  /** 访问令牌。只放内存（见 `store.ts` 的说明） */
  accessToken: string | null;
  /** 刷新令牌。持久化，用于刷新页面后恢复会话 */
  refreshToken: string | null;
  /** 访问令牌到期时刻（毫秒时间戳）；未知为 `null` */
  expiresAt: number | null;
}

/** 会话快照。**引用稳定**：内容不变时反复取到的是同一个对象 */
export interface SessionSnapshot {
  user: SessionUser | null;
  tokens: SessionTokens;
  /** 是否已建立会话（访问令牌与刷新令牌至少有一个） */
  authenticated: boolean;
}

/** 键值存储。与 Web Storage 同形，便于注入 `localStorage` 或内存实现 */
export interface SessionStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

/** 会话变更监听器 */
export type SessionListener = (snapshot: SessionSnapshot, previous: SessionSnapshot) => void;

/** 建立或更新会话的入参 */
export interface SessionUpdate {
  user: SessionUser;
  tokens?: Partial<SessionTokens>;
}

/** 会话存储 */
export interface SessionStore {
  /** 当前快照。`useSyncExternalStore` 等消费方要求引用稳定 */
  getSnapshot(): SessionSnapshot;
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  /** 是否已认证 */
  isAuthenticated(): boolean;
  /** 是否具备某个权限 */
  hasPermission(permission: string): boolean;
  /** 建立或覆盖会话（登录、刷新、会话恢复都走它） */
  set(update: SessionUpdate): void;
  /** 只更新身份与权限（令牌不动） */
  updateUser(user: SessionUser): void;
  /** 只更新令牌（刷新令牌时用） */
  updateTokens(tokens: Partial<SessionTokens>): void;
  /** 清空会话（登出、令牌失效） */
  clear(): void;
  /** 订阅变更，返回取消订阅函数 */
  subscribe(listener: SessionListener): () => void;
}
