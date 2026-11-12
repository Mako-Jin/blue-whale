// 租户管理 API（经网关 → whale-service-auth /tenant）
// Wave 5.54 Phase 4：仅平台租户（tenant_type=PLATFORM）可访问，业务租户返回 403

import { del, get, post, put, type PageResult } from './client';

export type TenantType = 'PLATFORM' | 'TENANT';

export interface TenantVO {
  id: string;
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
  return get<PageResult<TenantVO>>('/tenant/v1/page', { ...params });
}

export function getTenant(id: string): Promise<TenantVO> {
  return get<TenantVO>(`/tenant/v1/${id}`);
}

/** 开通租户：创建租户并初始化管理员、默认角色、菜单与部门根 */
export function createTenant(payload: TenantCreatePayload): Promise<TenantVO> {
  return post<TenantVO>('/tenant/v1', payload);
}

export function updateTenant(id: string, payload: TenantUpdatePayload): Promise<TenantVO> {
  return put<TenantVO>(`/tenant/v1/${id}`, payload);
}

export function changeTenantStatus(id: string, status: number): Promise<TenantVO> {
  return put<TenantVO>(`/tenant/v1/${id}/status`, { status });
}

/** 续期：新到期时间必须晚于当前时间（yyyy-MM-dd HH:mm:ss） */
export function renewTenant(id: string, expireTime: string): Promise<TenantVO> {
  return put<TenantVO>(`/tenant/v1/${id}/renew`, { expireTime });
}

/** 租户成员内的角色项（Wave 5.54 Phase 5.3） */
export interface TenantMemberRoleVO {
  id: string;
  roleCode: string;
  roleName: string;
}

/** 当前租户成员：启用关系 + 本租户角色 */
export interface TenantMemberVO {
  userId: string;
  username: string;
  nickname?: string;
  phone?: string;
  /** 用户状态：1 启用 / 0 停用（全局生效） */
  status?: number;
  roles: TenantMemberRoleVO[];
}

/** 查询当前租户成员（GET /tenant/v1/members） */
export function listTenantMembers(): Promise<TenantMemberVO[]> {
  return get<TenantMemberVO[]>('/tenant/v1/members');
}

/** 全量覆盖成员在本租户内的角色 */
export function assignTenantMemberRoles(userId: string, roleIds: string[]): Promise<void> {
  return put<void>(`/tenant/v1/members/${userId}/roles`, { roleIds });
}

/** 租户口令策略视图（REQ-SEC-023）：生效值 + 全局默认值 */
export interface TenantPasswordPolicyVO {
  tenantId: string;
  /** 生效的口令有效期（天） */
  maxAgeDays: number;
  /** 生效的到期前提醒天数 */
  warnDays: number;
  /** false 表示沿用全局默认（未自定义） */
  customized: boolean;
  defaultMaxAgeDays: number;
  defaultWarnDays: number;
}

/** 查询本租户生效口令策略（GET /tenant/v1/password-policy，租户管理员/平台管理员） */
export function getTenantPasswordPolicy(): Promise<TenantPasswordPolicyVO> {
  return get<TenantPasswordPolicyVO>('/tenant/v1/password-policy');
}

/** 保存本租户口令策略（PUT /tenant/v1/password-policy，有效期 1–365 天，提醒天数 0–有效期） */
export function updateTenantPasswordPolicy(payload: {
  maxAgeDays: number;
  warnDays: number;
}): Promise<TenantPasswordPolicyVO> {
  return put<TenantPasswordPolicyVO>('/tenant/v1/password-policy', payload);
}

/** 平台租户：为用户绑定可进入的租户 */
export function bindUserTenant(userId: string, tenantId: string): Promise<void> {
  return post<void>(`/user/v1/${userId}/tenants`, { tenantId });
}

/** 平台租户：解除用户与租户的关系（首页关系由后端拒绝） */
export function unbindUserTenant(userId: string, tenantId: string): Promise<void> {
  return del<void>(`/user/v1/${userId}/tenants/${tenantId}`);
}

// ---------------------------------------------------------------------------
// 租户配置（Wave 6.19）：权限中心通用配置的租户默认值层
//
// 读取优先级「用户配置 > 租户配置 > 消费方默认」，本层由租户管理员维护；
// 值一律 JSON 字符串（≤8KB），auth 只存取不解释语义，键前缀分域（notify.* / ui.*）。
// ---------------------------------------------------------------------------

/** 租户配置条目 */
export interface TenantConfigEntry {
  /** 租户 ID */
  tenantId: string;
  /** 配置键（前缀分域，如 notify.twin.alert） */
  configKey: string;
  /** 配置值（JSON 字符串） */
  configValue: string;
}

/** 查询本租户配置（prefix 为空表示全部） */
export function listTenantConfig(prefix?: string): Promise<TenantConfigEntry[]> {
  return get<TenantConfigEntry[]>('/tenant/v1/config', prefix ? { prefix } : undefined);
}

/** 保存一条租户配置（存在则覆盖） */
export function upsertTenantConfig(payload: {
  configKey: string;
  configValue: string;
}): Promise<void> {
  return put<void>('/tenant/v1/config', payload);
}
