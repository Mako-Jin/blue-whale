// 运维平台登录态读写入口（localStorage，key 前缀 whale_ops_）

const ACCESS_TOKEN_KEY = 'whale_ops_token';
const USER_KEY = 'whale_ops_user';

/** GET /auth/me 返回的当前用户信息 */
export interface OpsUserInfo {
  userId?: number | string;
  username: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
}

/** POST /auth/login 返回的登录响应 */
export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  username?: string;
  nickname?: string;
  roles?: string[];
}

export function getToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function saveToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function getCurrentUser(): OpsUserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as OpsUserInfo;
  } catch {
    return null;
  }
}

export function saveCurrentUser(user: OpsUserInfo): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** 登录成功后落盘令牌与用户基础信息 */
export function saveLogin(data: LoginResponse): void {
  saveToken(data.accessToken);
  const current = getCurrentUser();
  saveCurrentUser({
    userId: current?.userId,
    username: data.username ?? current?.username ?? '',
    nickname: data.nickname ?? current?.nickname,
    roles: data.roles ?? current?.roles ?? [],
    permissions: current?.permissions ?? [],
  });
}

/** 清除令牌与用户缓存（登出 / 401 失效） */
export function clearAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
