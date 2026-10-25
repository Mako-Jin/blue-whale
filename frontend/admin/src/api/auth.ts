// whale-auth 真实认证 API 客户端（经网关转发）
// 后端统一响应体：{ code, message, data }，code=0 表示成功
// 本模块是「登出令牌黑名单」前端联调入口（REQ-SEC-003）

import { AppEvents, eventBus } from '@bluewhale/whale-common-tools';

import { get, post } from './client';
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
// AUTH-39 一次一换：显式刷新入口（401 自动续期链路由 http 客户端内置，见 http.ts#refreshSession）
export { refreshSession as refresh } from './client';

// 登录：POST /auth/login（网关白名单，免 JWT）
// tenantCode 为租户编码，留空由后端回落到默认租户 default（Wave 5.54 Phase 0）
export async function login(username: string, password: string, tenantCode?: string): Promise<LoginResponse> {
  const data = await post<LoginResponse>('/auth/login', {
    tenantCode: tenantCode?.trim() || undefined,
    username,
    password,
  });
  saveLogin(data, tenantCode);
  // 审计：登录成功留痕（敏感字段由 tracker 统一脱敏）
  eventBus.emit(AppEvents.AUTH_LOGIN, { username, roles: data.roles });
  return data;
}

// 当前登录用户：GET /auth/me（需 accessToken）——用于校验令牌有效性并刷新权限码
export async function fetchMe(): Promise<AuthMeResponse> {
  const me = await get<AuthMeResponse>('/auth/me');
  const user = getCurrentUser();
  const beforePermissions = (user?.permissions ?? []).join(',');
  saveCurrentUser({
    ...(user ?? { username: me.username, nickname: me.nickname, roles: [] }),
    userId: me.userId,
    tenantId: me.tenantId ?? user?.tenantId,
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

/** 后端 MyTenantVO：GET /auth/tenants 列表项（Wave 5.54 Phase 5.1） */
export interface MyTenantVO {
  tenantId: number | string;
  tenantCode: string;
  tenantName: string;
  /** 是否为当前令牌所在租户 */
  current: boolean;
}

// 我的租户：GET /auth/tenants（仅需登录）——切换器数据源
export async function listMyTenants(): Promise<MyTenantVO[]> {
  return get<MyTenantVO[]>('/auth/tenants');
}

// 切换租户：POST /auth/tenant/switch（Wave 5.54 Phase 5.2）
// 成功即落盘新令牌与目标租户编码；失败不改变本地登录态，由调用方提示
export async function switchTenant(tenantId: number | string, tenantCode: string): Promise<LoginResponse> {
  const data = await post<LoginResponse>('/auth/tenant/switch', {
    tenantId,
    refreshToken: getRefreshToken(),
  });
  saveLogin(data, tenantCode);
  return data;
}

// 登出：POST /auth/logout（需 accessToken）——后端将 accessToken 与 refreshToken 写入 Redis 黑名单
export async function logout(): Promise<void> {
  const user = getCurrentUser();
  try {
    await post<void>('/auth/logout', { refreshToken: getRefreshToken() });
  } catch {
    // 登出接口异常（离线/网络故障）不阻断本地登出，保证用户始终能退出
  } finally {
    // 审计：无论服务端登出是否成功，本地退出一律留痕（先于清态，保证上报能解析到当前用户）
    eventBus.emit(AppEvents.AUTH_LOGOUT, { userId: user?.userId, username: user?.username });
    clearAuth();
  }
}
