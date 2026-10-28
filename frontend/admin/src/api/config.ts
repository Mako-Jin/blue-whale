// 业务配置服务 API（经网关 → whale-config /config）
// 权限码：sys:config:list/add/edit/delete/rollback（246–250）

import { del, get, post, put, type PageResult } from './client';

/** 配置项（字段对齐后端 ConfigItemVO） */
export interface ConfigItemVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  configKey: string;
  configValue?: string;
  /** STRING / NUMBER / BOOLEAN / JSON */
  valueType: string;
  description?: string;
  version: number;
  /** 1 启用 / 0 停用 */
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 配置项新增入参 */
export interface ConfigItemCreateInput {
  configKey: string;
  configValue?: string;
  valueType?: string;
  description?: string;
  status?: number;
}

/** 配置项编辑入参（配置键不可变） */
export interface ConfigItemUpdateInput {
  configValue?: string;
  valueType?: string;
  description?: string;
  status?: number;
}

/** 配置项分页查询参数 */
export interface ConfigItemPageParams {
  keyword?: string;
  status?: number;
  valueType?: string;
  current: number;
  size: number;
}

/** 配置变更历史（字段对齐后端 ConfigItemHistoryVO） */
export interface ConfigItemHistoryVO {
  id: string;
  itemId: string;
  configKey: string;
  /** CREATE / UPDATE / ROLLBACK / DELETE */
  operation: string;
  oldValue?: string | null;
  newValue?: string | null;
  version: number;
  operatorId?: string | null;
  operator?: string | null;
  createTime?: string;
}

/** 分页查询配置项 */
export function pageConfigItems(params: ConfigItemPageParams): Promise<PageResult<ConfigItemVO>> {
  return get<PageResult<ConfigItemVO>>('/config/item/page', { ...params });
}

/** 创建配置项 */
export function createConfigItem(body: ConfigItemCreateInput): Promise<ConfigItemVO> {
  return post<ConfigItemVO>('/config/item', body);
}

/** 更新配置项（值变更版本号 +1） */
export function updateConfigItem(id: string, body: ConfigItemUpdateInput): Promise<ConfigItemVO> {
  return put<ConfigItemVO>(`/config/item/${id}`, body);
}

/** 删除配置项（逻辑删除） */
export function deleteConfigItem(id: string): Promise<void> {
  return del<void>(`/config/item/${id}`);
}

/** 查询配置项变更历史（按版本号倒序） */
export function listConfigItemHistory(id: string): Promise<ConfigItemHistoryVO[]> {
  return get<ConfigItemHistoryVO[]>(`/config/item/${id}/history`);
}

/** 回滚配置值到目标版本（产生新版本） */
export function rollbackConfigItem(id: string, targetVersion: number): Promise<ConfigItemVO> {
  return post<ConfigItemVO>(`/config/item/${id}/rollback`, { targetVersion });
}
