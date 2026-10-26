// 菜单管理 API（经网关 → whale-service-auth /menu）

import { del, get, post, put } from './client';

/** 菜单类型：DIR 目录 / MENU 菜单 */
export type MenuType = 'DIR' | 'MENU';

export interface MenuVO {
  id: number;
  parentId: number;
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
  parentId?: number;
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
  return get<MenuVO[]>('/menu/tree');
}

export function getMenu(id: number): Promise<MenuVO> {
  return get<MenuVO>(`/menu/${id}`);
}

export function createMenu(payload: MenuPayload): Promise<void> {
  return post<void>('/menu', payload);
}

export function updateMenu(id: number, payload: MenuPayload): Promise<void> {
  return put<void>(`/menu/${id}`, payload);
}

export function deleteMenu(id: number): Promise<void> {
  return del<void>(`/menu/${id}`);
}
