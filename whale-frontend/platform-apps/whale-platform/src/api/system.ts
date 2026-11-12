// system.ts —— 基座系统管理只读查询（用户 / 角色 / 菜单）
//
// 后端：whale-service-auth 经网关转发，分页参数 current/size，响应为 Result<PageResult<T>>。

import { get, type PageResult } from './client';

/** 用户视图对象（字段对齐 backend UserVO） */
export interface UserVO {
  id: number;
  username: string;
  nickname?: string;
  phone?: string;
  email?: string;
  deptId?: number;
  status?: number;
  lastLoginTime?: string;
  createTime?: string;
}

/** 角色视图对象（字段对齐 backend RoleVO） */
export interface RoleVO {
  id: number;
  roleCode: string;
  roleName: string;
  description?: string;
  sortNo?: number;
  status?: number;
  createTime?: string;
}

/** 菜单视图对象（字段对齐 backend MenuVO） */
export interface MenuVO {
  id: number;
  parentId?: number;
  name: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  menuType?: string;
  permissionCode?: string;
  visible?: number;
  status?: number;
  children?: MenuVO[];
}

/** 拼装分页查询串（跳过空值） */
function buildQuery(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询用户（keyword 匹配用户名/昵称/手机号） */
export function pageUsers(params: {
  current?: number;
  size?: number;
  keyword?: string;
}): Promise<PageResult<UserVO>> {
  return get<PageResult<UserVO>>(`/user/v1/page${buildQuery(params)}`);
}

/** 分页查询角色（keyword 匹配角色编码/名称） */
export function pageRoles(params: {
  current?: number;
  size?: number;
  keyword?: string;
}): Promise<PageResult<RoleVO>> {
  return get<PageResult<RoleVO>>(`/role/v1/page${buildQuery(params)}`);
}

/** 查询菜单树 */
export function treeMenus(): Promise<MenuVO[]> {
  return get<MenuVO[]>('/menu/v1/tree');
}
