// 角色约束 API（经网关 → whale-service-auth /role-constraint）
// 权限码：sys:role:constraint:list/add/edit/delete（2405–2408）

import { del, get, post, put, type PageResult } from './client';

/** 角色约束（字段对齐后端 RoleConstraintResponse） */
export interface RoleConstraintVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  tenantId?: string;
  constraintCode: string;
  constraintName: string;
  /** MUTEX 互斥 / CARDINALITY 基数 */
  constraintType: string;
  /** 受约束的角色 ID 列表（后端 Long 序列化为字符串） */
  roleIds: string[];
  /** CARDINALITY 约束的最大持有角色数 */
  maxAssign?: number;
  /** 1 启用 / 0 停用 */
  status?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/** 角色约束保存入参（字段对齐后端 RoleConstraintSaveRequest） */
export interface RoleConstraintSavePayload {
  constraintCode: string;
  constraintName: string;
  constraintType: string;
  roleIds?: string[];
  maxAssign?: number;
  status?: number;
  description?: string;
}

export interface RoleConstraintPageParams {
  keyword?: string;
  constraintType?: string;
  current: number;
  size: number;
}

/** 分页查询角色约束 */
export function pageRoleConstraints(
  params: RoleConstraintPageParams,
): Promise<PageResult<RoleConstraintVO>> {
  return get<PageResult<RoleConstraintVO>>('/role-constraint/v1/page', { ...params });
}

/** 新增角色约束 */
export function createRoleConstraint(
  payload: RoleConstraintSavePayload,
): Promise<RoleConstraintVO> {
  return post<RoleConstraintVO>('/role-constraint/v1', payload);
}

/** 编辑角色约束 */
export function updateRoleConstraint(
  id: string,
  payload: RoleConstraintSavePayload,
): Promise<RoleConstraintVO> {
  return put<RoleConstraintVO>(`/role-constraint/v1/${id}`, payload);
}

/** 删除角色约束 */
export function deleteRoleConstraint(id: string): Promise<void> {
  return del<void>(`/role-constraint/v1/${id}`);
}
