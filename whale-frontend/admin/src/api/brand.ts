// 品牌配置 API（经网关 → whale-service-auth /brand）
// 权限码：sys:brand:list/add/edit/delete（2370–2373）

import { del, get, post, put, type PageResult } from './client';

/** 品牌配置（字段对齐后端 BrandConfigResponse） */
export interface BrandVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  /** 作用域类型（PLATFORM / TENANT 等） */
  targetType: string;
  /** 作用域 ID（后端 Long 序列化为字符串） */
  targetId?: string;
  domain?: string;
  brandName: string;
  logoUrl?: string;
  loginTitle?: string;
  institutionName?: string;
  copyright?: string;
  primaryColor?: string;
  backgroundUrl?: string;
  priority?: number;
  /** 1 启用 / 0 停用 */
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 品牌配置保存入参（字段对齐后端 BrandConfigSaveRequest） */
export interface BrandSavePayload {
  targetType: string;
  targetId?: string;
  domain?: string;
  brandName: string;
  logoUrl?: string;
  loginTitle?: string;
  institutionName?: string;
  copyright?: string;
  primaryColor?: string;
  backgroundUrl?: string;
  priority?: number;
  status?: number;
}

export interface BrandPageParams {
  keyword?: string;
  targetType?: string;
  status?: number;
  current: number;
  size: number;
}

/** 分页查询品牌配置 */
export function pageBrands(params: BrandPageParams): Promise<PageResult<BrandVO>> {
  return get<PageResult<BrandVO>>('/brand/v1/page', { ...params });
}

/** 新增品牌配置 */
export function createBrand(payload: BrandSavePayload): Promise<BrandVO> {
  return post<BrandVO>('/brand/v1', payload);
}

/** 编辑品牌配置 */
export function updateBrand(id: string, payload: BrandSavePayload): Promise<BrandVO> {
  return put<BrandVO>(`/brand/v1/${id}`, payload);
}

/** 删除品牌配置（逻辑删除） */
export function deleteBrand(id: string): Promise<void> {
  return del<void>(`/brand/v1/${id}`);
}
