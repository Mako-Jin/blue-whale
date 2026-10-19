// 登录态与用户缓存的唯一读写入口（localStorage）

const ACCESS_TOKEN_KEY = 'whale_admin_access_token';
const REFRESH_TOKEN_KEY = 'whale_admin_refresh_token';
const USER_KEY = 'whale_admin_user';

export interface UserInfo {
  userId?: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions?: string[];
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  nickname: string;
  roles: string[];
}

export interface AuthMeResponse {
  userId: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions: string[];
  menus: unknown[];
}

export function getToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

export function saveCurrentUser(user: UserInfo): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** 登录成功后落盘令牌与用户基础信息 */
export function saveLogin(data: LoginResponse): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
  saveCurrentUser({
    username: data.username,
    nickname: data.nickname,
    roles: data.roles ?? [],
  });
}

export function clearAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
