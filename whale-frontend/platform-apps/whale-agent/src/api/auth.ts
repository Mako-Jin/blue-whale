// whale-service-auth 真实认证 API 客户端（经网关转发）——复用 workflow-designer 版实现

import { AppEvents, eventBus } from '@bluewhale/whale-common-tools';

import { get, post } from './client';
import {
  clearAuth,
  getCurrentUser,
  saveCurrentUser,
  saveLogin,
  type AuthMeResponse,
  type LoginResponse,
} from './token';

export type { AuthMeResponse, LoginResponse, UserInfo } from './token';
export { bootstrapAuth, clearAuth, getCurrentUser, getToken } from './token';
// 显式刷新入口（401 自动续期链路由 http 客户端内置）
export { refreshSession as refresh } from './client';

// 登录：POST /auth/login（网关白名单，免 JWT）
export async function login(
  username: string,
  password: string,
  tenantCode?: string,
): Promise<LoginResponse> {
  const data = await post<LoginResponse>('/auth/v1/login', {
    tenantCode: tenantCode?.trim() || undefined,
    username,
    password,
  });
  saveLogin(data);
  // 审计：登录成功留痕（敏感字段由 tracker 统一脱敏）
  eventBus.emit(AppEvents.AUTH_LOGIN, { username, roles: data.roles });
  return data;
}

// 当前登录用户：GET /auth/me（需 accessToken）——用于校验令牌有效性并刷新权限码
export async function fetchMe(): Promise<AuthMeResponse> {
  const me = await get<AuthMeResponse>('/auth/v1/me');
  const user = getCurrentUser();
  const beforePermissions = (user?.permissions ?? []).join(',');
  saveCurrentUser({
    ...(user ?? { username: me.username, nickname: me.nickname, roles: [] }),
    userId: me.userId,
    username: me.username,
    nickname: me.nickname,
    roles: me.roles ?? [],
    permissions: me.permissions ?? [],
    menus: me.menus ?? [],
  });
  // 审计：权限集发生变化（含首次授权）时留痕
  if (beforePermissions !== (me.permissions ?? []).join(',')) {
    eventBus.emit(AppEvents.AUTH_PERMISSION_CHANGE, {
      userId: me.userId,
      username: me.username,
      permissions: me.permissions ?? [],
    });
  }
  return me;
}

// 登出：POST /auth/logout（需 accessToken；CSRF 头由 http 客户端自动回传，服务端清刷新 Cookie）
export async function logout(): Promise<void> {
  const user = getCurrentUser();
  try {
    await post<void>('/auth/v1/logout');
  } catch {
    // 登出接口异常（离线/网络故障）不阻断本地登出，保证用户始终能退出
  } finally {
    // 审计：无论服务端登出是否成功，本地退出一律留痕（先于清态，保证上报能解析到当前用户）
    eventBus.emit(AppEvents.AUTH_LOGOUT, { userId: user?.userId, username: user?.username });
    clearAuth();
  }
}
