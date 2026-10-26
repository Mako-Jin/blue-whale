// whale-service-auth 认证 API 客户端（经网关转发）
// 后端统一响应体：{ code, message, data }，code=0 表示成功

import { AppEvents, eventBus } from '@bluewhale/whale-common-tools';

import { get, post } from './client';
import { getCurrentUser, saveCurrentUser, saveLogin, type LoginResponse } from './token';

export type { LoginResponse, OpsUserInfo } from './token';
export { clearAuth, getCurrentUser, getToken } from './token';

export interface LoginPayload {
  /** 租户编码，留空由后端回落到默认租户 default（Wave 5.54 Phase 0） */
  tenantCode?: string;
  username: string;
  password: string;
}

/** GET /auth/me 返回的当前登录用户（含权限码） */
export interface MeResponse {
  userId: number | string;
  username: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
}

// 登录：POST /auth/login（网关白名单，免 JWT）
export async function login(payload: LoginPayload): Promise<LoginResponse> {
  const data = await post<LoginResponse>('/auth/login', {
    ...payload,
    tenantCode: payload.tenantCode?.trim() || undefined,
  });
  saveLogin(data);
  // 审计：登录成功留痕（敏感字段由 tracker 统一脱敏）
  eventBus.emit(AppEvents.AUTH_LOGIN, { username: payload.username, roles: data.roles });
  return data;
}

// 当前登录用户：GET /auth/me（需 accessToken）——校验令牌有效性并刷新用户信息
export async function me(): Promise<MeResponse> {
  const data = await get<MeResponse>('/auth/me');
  const current = getCurrentUser();
  saveCurrentUser({
    userId: data.userId,
    username: data.username,
    nickname: data.nickname,
    roles: data.roles ?? current?.roles ?? [],
    permissions: data.permissions ?? [],
  });
  return data;
}
