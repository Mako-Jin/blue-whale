// 租户管理 API（经网关 → whale-service-auth /tenant）
// Wave 5.54 Phase 4：仅平台租户（tenant_type=PLATFORM）可访问，业务租户返回 403

import { del, get, post, put, type PageResult } from './client';

export type TenantType = 'PLATFORM' | 'TENANT';

export interface TenantVO {
  id: number;
  tenantCode: string;
  tenantName: string;
  tenantType: TenantType;
  /** 1 启用 / 0 停用 */
  status?: number;
  /** 到期时间，为空表示长期有效 */
  expireTime?: string;
  /** 配额 JSON，如 {"maxUsers":50,"maxStorageMb":1024} */
  quotaJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface TenantPageParams {
  keyword?: string;
  status?: number;
  current: number;
  size: number;
}

export interface TenantCreatePayload {
  tenantCode: string;
  tenantName: string;
  expireTime?: string;
  quotaJson?: string;
  adminUsername: string;
  adminPassword: string;
  adminNickname?: string;
}

export interface TenantUpdatePayload {
  tenantName?: string;
  quotaJson?: string;
}

export function pageTenants(params: TenantPageParams): Promise<PageResult<TenantVO>> {
  return get<PageResult<TenantVO>>('/tenant/page', { ...params });
}

export function getTenant(id: number): Promise<TenantVO> {
  return get<TenantVO>(`/tenant/${id}`);
}

/** 开通租户：创建租户并初始化管理员、默认角色、菜单与部门根 */
export function createTenant(payload: TenantCreatePayload): Promise<TenantVO> {
  return post<TenantVO>('/tenant', payload);
}

export function updateTenant(id: number, payload: TenantUpdatePayload): Promise<TenantVO> {
  return put<TenantVO>(`/tenant/${id}`, payload);
}

export function changeTenantStatus(id: number, status: number): Promise<TenantVO> {
  return put<TenantVO>(`/tenant/${id}/status`, { status });
}

/** 续期：新到期时间必须晚于当前时间（yyyy-MM-dd HH:mm:ss） */
export function renewTenant(id: number, expireTime: string): Promise<TenantVO> {
  return put<TenantVO>(`/tenant/${id}/renew`, { expireTime });
}

/** 租户成员内的角色项（Wave 5.54 Phase 5.3） */
export interface TenantMemberRoleVO {
  id: number;
  roleCode: string;
  roleName: string;
}

/** 当前租户成员：启用关系 + 本租户角色 */
export interface TenantMemberVO {
  userId: number;
  username: string;
  nickname?: string;
  phone?: string;
  /** 用户状态：1 启用 / 0 停用（全局生效） */
  status?: number;
  roles: TenantMemberRoleVO[];
}

/** 查询当前租户成员（GET /tenant/members） */
export function listTenantMembers(): Promise<TenantMemberVO[]> {
  return get<TenantMemberVO[]>('/tenant/members');
}

/** 全量覆盖成员在本租户内的角色 */
export function assignTenantMemberRoles(userId: number, roleIds: number[]): Promise<void> {
  return put<void>(`/tenant/members/${userId}/roles`, { roleIds });
}

/** 平台租户：为用户绑定可进入的租户 */
export function bindUserTenant(userId: number, tenantId: number): Promise<void> {
  return post<void>(`/user/${userId}/tenants`, { tenantId });
}

/** 平台租户：解除用户与租户的关系（首页关系由后端拒绝） */
export function unbindUserTenant(userId: number, tenantId: number): Promise<void> {
  return del<void>(`/user/${userId}/tenants/${tenantId}`);
}
