// 权限策略 API（经网关 → whale-service-auth /perm-strategy）
// 权限码：sys:perm:strategy:list/add/edit/delete/set-default（2400–2404）

import { del, get, post, put, type PageResult } from './client';

/** 权限策略（字段对齐后端 PermissionStrategyResponse） */
export interface PermStrategyVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  tenantId?: string;
  strategyCode: string;
  strategyName: string;
  /** GRANT 附加授权 / DENY 显式拒绝 */
  strategyType: string;
  /** 生效角色 ID 列表（后端 Long 序列化为字符串） */
  roleIds: string[];
  /** 生效资源 ID 列表（后端 Long 序列化为字符串） */
  resourceIds: string[];
  /** 附加条件 JSON */
  conditionJson?: string;
  defaultStrategy: boolean;
  priority?: number;
  /** 1 启用 / 0 停用 */
  status?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/** 权限策略保存入参（字段对齐后端 PermissionStrategySaveRequest） */
export interface PermStrategySavePayload {
  strategyCode: string;
  strategyName: string;
  strategyType: string;
  roleIds?: string[];
  resourceIds?: string[];
  conditionJson?: string;
  isDefault?: boolean;
  priority?: number;
  status?: number;
  description?: string;
}

export interface PermStrategyPageParams {
  keyword?: string;
  current: number;
  size: number;
}

/** 分页查询权限策略 */
export function pagePermStrategies(
  params: PermStrategyPageParams,
): Promise<PageResult<PermStrategyVO>> {
  return get<PageResult<PermStrategyVO>>('/perm-strategy/v1/page', { ...params });
}

/** 新增权限策略 */
export function createPermStrategy(payload: PermStrategySavePayload): Promise<PermStrategyVO> {
  return post<PermStrategyVO>('/perm-strategy/v1', payload);
}

/** 编辑权限策略 */
export function updatePermStrategy(
  id: string,
  payload: PermStrategySavePayload,
): Promise<PermStrategyVO> {
  return put<PermStrategyVO>(`/perm-strategy/v1/${id}`, payload);
}

/** 删除权限策略 */
export function deletePermStrategy(id: string): Promise<void> {
  return del<void>(`/perm-strategy/v1/${id}`);
}

/** 设为该角色组默认策略 */
export function setDefaultPermStrategy(id: string): Promise<void> {
  return put<void>(`/perm-strategy/v1/${id}/default`);
}
