// 用户组 API（经网关 → whale-service-auth /user-group）
// 权限码：sys:user:group:list/add/edit/delete/member（2395–2399）

import { del, get, post, put, type PageResult } from './client';

/** 用户组（字段对齐后端 UserGroupResponse） */
export interface UserGroupVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  tenantId?: string;
  groupCode: string;
  groupName: string;
  parentId?: string;
  /** 组类别（如 STATIC / DYNAMIC；DYNAMIC 组由 filters 规则动态圈人，不可手工维护成员） */
  category?: string;
  /** 动态组过滤规则 JSON */
  filtersJson?: string;
  description?: string;
  sortNo?: number;
  /** 1 启用 / 0 停用 */
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 用户组保存入参（字段对齐后端 UserGroupSaveRequest） */
export interface UserGroupSavePayload {
  groupCode: string;
  groupName: string;
  parentId?: string;
  category?: string;
  filtersJson?: string;
  description?: string;
  sortNo?: number;
  status?: number;
}

export interface UserGroupPageParams {
  keyword?: string;
  category?: string;
  current: number;
  size: number;
}

/** 分页查询用户组 */
export function pageUserGroups(params: UserGroupPageParams): Promise<PageResult<UserGroupVO>> {
  return get<PageResult<UserGroupVO>>('/user-group/v1/page', { ...params });
}

/** 新增用户组 */
export function createUserGroup(payload: UserGroupSavePayload): Promise<UserGroupVO> {
  return post<UserGroupVO>('/user-group/v1', payload);
}

/** 编辑用户组 */
export function updateUserGroup(id: string, payload: UserGroupSavePayload): Promise<UserGroupVO> {
  return put<UserGroupVO>(`/user-group/v1/${id}`, payload);
}

/** 删除用户组 */
export function deleteUserGroup(id: string): Promise<void> {
  return del<void>(`/user-group/v1/${id}`);
}

/** 查询组成员用户 ID 列表 */
export function listUserGroupMembers(id: string): Promise<string[]> {
  return get<string[]>(`/user-group/v1/${id}/members`);
}

/** 全量替换组成员（DYNAMIC 组不可手工维护） */
export function replaceUserGroupMembers(id: string, memberUserIds: string[]): Promise<void> {
  return put<void>(`/user-group/v1/${id}/members`, { memberUserIds });
}
