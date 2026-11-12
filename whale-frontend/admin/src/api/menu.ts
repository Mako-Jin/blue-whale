// 菜单管理 API（经网关 → whale-service-auth /menu）

import { del, get, post, put } from './client';

/** 菜单类型：DIR 目录 / MENU 菜单 */
export type MenuType = 'DIR' | 'MENU';

export interface MenuVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  parentId: string;
  name: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  menuType?: MenuType | string;
  permissionCode?: string;
  visible?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
  children?: MenuVO[];
}

export interface MenuPayload {
  parentId?: string;
  name: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  menuType?: MenuType | string;
  permissionCode?: string;
  visible?: number;
  status?: number;
}

export function treeMenus(): Promise<MenuVO[]> {
  return get<MenuVO[]>('/menu/v1/tree');
}

export function getMenu(id: string): Promise<MenuVO> {
  return get<MenuVO>(`/menu/v1/${id}`);
}

export function createMenu(payload: MenuPayload): Promise<void> {
  return post<void>('/menu/v1', payload);
}

export function updateMenu(id: string, payload: MenuPayload): Promise<void> {
  return put<void>(`/menu/v1/${id}`, payload);
}

export function deleteMenu(id: string): Promise<void> {
  return del<void>(`/menu/v1/${id}`);
}
