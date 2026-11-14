// 会话管理 API（经网关 → whale-service-auth /session）
// 权限码：sys:session:list(2409) / sys:session:revoke(2410)

import { del, get, type PageResult } from './client';

/** 用户会话（字段对齐后端 SessionResponse） */
export interface SessionVO {
  /** JWT ID（撤销目标） */
  jti: string;
  /** 会话族 ID（同一登录链路轮换共享） */
  familyId?: string;
  tenantId?: string;
  /** 后端 Long 序列化为字符串 */
  userId: string;
  issuedAt?: string;
  expiresAt?: string;
  revoked: boolean;
  revokedAt?: string;
  revokedReason?: string;
  deviceId?: string;
}

export interface SessionPageParams {
  userId?: string;
  /** 仅看未撤销会话 */
  onlyActive?: boolean;
  current: number;
  size: number;
}

/** 分页查询用户会话（userId 可空=查全部；管理员可查任意用户） */
export function pageSessions(params: SessionPageParams): Promise<PageResult<SessionVO>> {
  return get<PageResult<SessionVO>>('/session/v1/page', { ...params });
}

/** 按 jti 强制下线会话 */
export function revokeSession(jti: string): Promise<void> {
  return del<void>(`/session/v1/${jti}`);
}
