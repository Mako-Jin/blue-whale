// AI 助手平台 API（经网关 → whale-assistant /assistant）
// 权限码：sys:assistant:define(2300) / sys:assistant:chat(2301)

import { del, get, post, put, type PageResult } from './client';

/** 助手定义（字段对齐后端 DefinitionVO） */
export interface DefinitionVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  code: string;
  name?: string;
  avatar?: string;
  description?: string;
  systemPrompt?: string;
  modelParams?: Record<string, unknown> | null;
  knowledgeScope?: string;
  /** draft / published / disabled */
  status: string;
  toolWhitelist: string[];
  createdBy?: string | null;
  createTime?: string;
  updateTime?: string;
}

/** 助手定义保存入参（编码创建后不可变） */
export interface DefinitionSaveInput {
  code: string;
  name?: string;
  avatar?: string;
  description?: string;
  systemPrompt?: string;
  /** 仅允许 model / temperature / max_tokens */
  modelParams?: Record<string, unknown>;
  knowledgeScope?: string;
}

/** 助手定义分页查询参数 */
export interface DefinitionPageParams {
  keyword?: string;
  status?: string;
  current: number;
  size: number;
}

/** 工具注册表条目（whale-ai /v1/tools 透传） */
export interface ToolInfoVO {
  name: string;
  description: string;
  /** L0 / L1 / L2 */
  riskLevel: string;
  permission: string;
}

/** 用量看板行（助手×天聚合） */
export interface UsageVO {
  assistantId: string;
  assistantName: string;
  assistantCode: string;
  statDate: string;
  sessionCount: number;
  messageCount: number;
  completionTokens: number;
}

/** 用量看板查询参数 */
export interface UsagePageParams {
  assistantId?: string;
  startDate?: string;
  endDate?: string;
  current: number;
  size: number;
}

/** 分页查询助手定义 */
export function pageDefinitions(params: DefinitionPageParams): Promise<PageResult<DefinitionVO>> {
  return get<PageResult<DefinitionVO>>('/assistant/v1/definitions/page', { ...params });
}

/** 创建助手（草稿态） */
export function createDefinition(body: DefinitionSaveInput): Promise<DefinitionVO> {
  return post<DefinitionVO>('/assistant/v1/definitions', body);
}

/** 更新助手（编码不可改） */
export function updateDefinition(id: string, body: DefinitionSaveInput): Promise<DefinitionVO> {
  return put<DefinitionVO>(`/assistant/v1/definitions/${id}`, body);
}

/** 发布助手 */
export function publishDefinition(id: string): Promise<DefinitionVO> {
  return post<DefinitionVO>(`/assistant/v1/definitions/${id}/publish`, {});
}

/** 停用助手 */
export function disableDefinition(id: string): Promise<DefinitionVO> {
  return post<DefinitionVO>(`/assistant/v1/definitions/${id}/disable`, {});
}

/** 删除助手（逻辑删除） */
export function deleteDefinition(id: string): Promise<void> {
  return del<void>(`/assistant/v1/definitions/${id}`);
}

/** 查看工具白名单 */
export function listToolWhitelist(id: string): Promise<string[]> {
  return get<string[]>(`/assistant/v1/definitions/${id}/tools`);
}

/** 覆盖式设置工具白名单（校验 whale-ai 注册表） */
export function setToolWhitelist(id: string, toolCodes: string[]): Promise<void> {
  return put<void>(`/assistant/v1/definitions/${id}/tools`, { toolCodes });
}

/** 查询 whale-ai 工具注册表（可授权工具全集） */
export function listRegisteredTools(): Promise<ToolInfoVO[]> {
  return get<ToolInfoVO[]>('/assistant/v1/tools');
}

/** 用量看板分页 */
export function pageUsage(params: UsagePageParams): Promise<PageResult<UsageVO>> {
  return get<PageResult<UsageVO>>('/assistant/v1/usage/page', { ...params });
}
