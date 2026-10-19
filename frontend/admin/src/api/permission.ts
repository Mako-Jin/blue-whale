// 权限点 API（经网关 → whale-auth /permission）

import { get, type PageResult } from './http';

export interface PermissionVO {
  id: number;
  parentId: number;
  permCode: string;
  permName: string;
  permType?: string;
  path?: string;
  method?: string;
  sortNo?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

export interface PermissionPageParams {
  keyword?: string;
  permType?: string;
  current: number;
  size: number;
}

export function pagePermissions(params: PermissionPageParams): Promise<PageResult<PermissionVO>> {
  return get<PageResult<PermissionVO>>('/permission/page', { ...params });
}
