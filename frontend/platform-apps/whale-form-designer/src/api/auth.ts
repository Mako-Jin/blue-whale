// whale-auth 真实认证 API 客户端（经网关转发）——复用 admin 版实现
// 后端统一响应体：{ code, message, data }，code=0 表示成功

import { get, post } from './http';
import {
  clearAuth,
  getCurrentUser,
  getRefreshToken,
  saveCurrentUser,
  saveLogin,
  type AuthMeResponse,
  type LoginResponse,
} from './token';

export type { AuthMeResponse, LoginResponse, UserInfo } from './token';
export { clearAuth, getCurrentUser, getRefreshToken, getToken } from './token';
// 显式刷新入口（401 自动续期链路由 http 客户端内置，见 http.ts#refreshSession）
export { refreshSession as refresh } from './http';

// 登录：POST /auth/login（网关白名单，免 JWT）
export async function login(username: string, password: string): Promise<LoginResponse> {
  const data = await post<LoginResponse>('/auth/login', { username, password });
  saveLogin(data);
  return data;
}

// 当前登录用户：GET /auth/me（需 accessToken）——用于校验令牌有效性并刷新权限码
export async function fetchMe(): Promise<AuthMeResponse> {
  const me = await get<AuthMeResponse>('/auth/me');
  const user = getCurrentUser();
  saveCurrentUser({
    ...(user ?? { username: me.username, nickname: me.nickname, roles: [] }),
    userId: me.userId,
    username: me.username,
    nickname: me.nickname,
    roles: me.roles ?? [],
    permissions: me.permissions ?? [],
    menus: me.menus ?? [],
  });
  return me;
}

// 登出：POST /auth/logout（需 accessToken）——后端将 accessToken 与 refreshToken 写入 Redis 黑名单
export async function logout(): Promise<void> {
  try {
    await post<void>('/auth/logout', { refreshToken: getRefreshToken() });
  } catch {
    // 登出接口异常（离线/网络故障）不阻断本地登出，保证用户始终能退出
  } finally {
    clearAuth();
  }
}
