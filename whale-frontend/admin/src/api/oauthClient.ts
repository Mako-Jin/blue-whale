// OAuth 客户端管理 API（经网关 → whale-service-auth /oauth-client）
// 权限码：sys:oauth:client:list/add/edit/secret/delete（2374–2378）
// 密钥仅创建/轮换时返回一次，之后不可查看。

import { del, get, post, put, type PageResult } from './client';

/** OAuth 客户端（字段对齐后端 OAuthClientResponse） */
export interface OAuthClientVO {
  /** 主键（后端 Long 序列化为字符串） */
  id: string;
  clientId: string;
  clientName: string;
  redirectUris: string[];
  grantTypes: string[];
  scopes: string[];
  /** access token 有效期（秒） */
  accessTokenTtl?: number;
  /** refresh token 有效期（秒） */
  refreshTokenTtl?: number;
  autoApprove?: boolean;
  /** 1 启用 / 0 停用 */
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 客户端保存入参（字段对齐后端 OAuthClientSaveRequest） */
export interface OAuthClientSavePayload {
  clientId: string;
  clientName: string;
  redirectUris?: string[];
  grantTypes?: string[];
  scopes?: string[];
  accessTokenTtl?: number;
  refreshTokenTtl?: number;
  autoApprove?: boolean;
  status?: number;
}

/** 创建/轮换密钥响应：clientSecret 仅此一次返回 */
export interface OAuthClientSecretVO {
  id: string;
  clientId: string;
  clientSecret: string;
}

export interface OAuthClientPageParams {
  keyword?: string;
  status?: number;
  current: number;
  size: number;
}

/** 分页查询 OAuth 客户端 */
export function pageOAuthClients(
  params: OAuthClientPageParams,
): Promise<PageResult<OAuthClientVO>> {
  return get<PageResult<OAuthClientVO>>('/oauth-client/v1/page', { ...params });
}

/** 注册客户端（clientSecret 仅本次返回，请立即妥善保存） */
export function createOAuthClient(payload: OAuthClientSavePayload): Promise<OAuthClientSecretVO> {
  return post<OAuthClientSecretVO>('/oauth-client/v1', payload);
}

/** 编辑客户端 */
export function updateOAuthClient(
  id: string,
  payload: OAuthClientSavePayload,
): Promise<OAuthClientVO> {
  return put<OAuthClientVO>(`/oauth-client/v1/${id}`, payload);
}

/** 轮换客户端密钥（旧密钥立即失效，新密钥仅本次返回） */
export function rotateOAuthClientSecret(id: string): Promise<OAuthClientSecretVO> {
  return post<OAuthClientSecretVO>(`/oauth-client/v1/${id}/secret`);
}

/** 停用客户端（逻辑删除） */
export function disableOAuthClient(id: string): Promise<void> {
  return del<void>(`/oauth-client/v1/${id}`);
}
