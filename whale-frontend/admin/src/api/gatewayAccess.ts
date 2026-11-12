// 网关访问名单 API（经网关 → whale-service-auth /auth/v1/gateway）
// 白名单 = 免鉴权路径；黑名单 = 封禁路径，且黑名单优先于白名单（命中即 403）。
// 网关每 60s 拉取一次，管理端写操作无需通知网关，最迟下一周期生效。

import { del, get, post, put } from './client';

export interface GatewayAccessRuleVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  /** 路径（Ant 通配，以 / 开头） */
  pathPattern: string;
  /** HTTP 方法，* 表示不限 */
  httpMethod: string;
  /** 作用域，当前仅 PLATFORM */
  scope: string;
  /** 来源：MIGRATION 迁移 / MANUAL 手工 */
  source: string;
  remark?: string | null;
  /** 1 启用/生效、0 停用/解除 */
  status: number;
  sortNo: number;
  createTime?: string;
  updateTime?: string;
}

/** 新增/更新名单条目请求体 */
export interface GatewayAccessRulePayload {
  pathPattern: string;
  /** 空或 * 表示不限方法 */
  httpMethod?: string;
  status?: number;
  sortNo?: number;
  remark?: string;
}

export function listGatewayWhitelist(status?: number): Promise<GatewayAccessRuleVO[]> {
  return get<GatewayAccessRuleVO[]>('/auth/v1/gateway/whitelist', { status });
}

export function createGatewayWhitelist(payload: GatewayAccessRulePayload): Promise<void> {
  return post<void>('/auth/v1/gateway/whitelist', payload);
}

export function updateGatewayWhitelist(
  id: string,
  payload: GatewayAccessRulePayload,
): Promise<void> {
  return put<void>(`/auth/v1/gateway/whitelist/${id}`, payload);
}

export function deleteGatewayWhitelist(id: string): Promise<void> {
  return del<void>(`/auth/v1/gateway/whitelist/${id}`);
}

export function listGatewayBlacklist(status?: number): Promise<GatewayAccessRuleVO[]> {
  return get<GatewayAccessRuleVO[]>('/auth/v1/gateway/blacklist', { status });
}

export function createGatewayBlacklist(payload: GatewayAccessRulePayload): Promise<void> {
  return post<void>('/auth/v1/gateway/blacklist', payload);
}

export function updateGatewayBlacklist(
  id: string,
  payload: GatewayAccessRulePayload,
): Promise<void> {
  return put<void>(`/auth/v1/gateway/blacklist/${id}`, payload);
}

export function deleteGatewayBlacklist(id: string): Promise<void> {
  return del<void>(`/auth/v1/gateway/blacklist/${id}`);
}
