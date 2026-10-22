// E2E 测试专用 API 薄封装：统一校验后端 { code, message, data } 信封。
// 仅测试进程使用，不参与应用构建。

import type { APIRequestContext, APIResponse } from '@playwright/test';
import { API_BASE_URL } from '../../playwright.config';

export const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'admin';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

/** 后端统一响应信封 */
interface Envelope<T> {
  code: number;
  message: string;
  data: T;
}

/** /auth/me 的菜单节点（仅取断言所需字段） */
export interface MenuNode {
  name: string;
  sort?: number;
}

export interface MeResponse {
  username: string;
  permissions: string[];
  menus: MenuNode[];
}

export interface PageResult<T> {
  records: T[];
  /** 后端 Long 走 ToStringSerializer，分页总数到达 JSON 时可能是字符串 */
  total: number | string;
}

export interface ColumnMeta {
  id: string;
  columnCode: string;
}

export interface TableDefinitionRecord {
  id: string;
  tableCode: string;
}

export interface TableDefinitionDetail extends TableDefinitionRecord {
  columns?: ColumnMeta[] | null;
}

export interface FormDefinitionRecord {
  id: string;
  code: string;
}

export interface FormDataRecord {
  id: string;
  definitionCode: string;
}

/** 读取信封；HTTP 非 2xx 或 code !== 0 时抛出带后端 message 的错误 */
async function readEnvelope<T>(response: APIResponse, label: string): Promise<T> {
  const body = (await response.json()) as Envelope<T>;
  if (!response.ok() || body.code !== 0) {
    throw new Error(
      `${label}失败：HTTP ${response.status()} code=${body.code} message=${body.message}`,
    );
  }
  return body.data;
}

/** 登录并返回 accessToken */
export async function login(
  ctx: APIRequestContext,
  username: string = ADMIN_USERNAME,
  password: string = ADMIN_PASSWORD,
): Promise<string> {
  const response = await ctx.post(`${API_BASE_URL}/auth/login`, {
    data: { username, password },
  });
  const data = await readEnvelope<{ accessToken: string }>(response, '登录');
  return data.accessToken;
}

/** GET /auth/me（角色/权限码/菜单） */
export async function fetchMe(ctx: APIRequestContext, token: string): Promise<MeResponse> {
  const response = await ctx.get(`${API_BASE_URL}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readEnvelope<MeResponse>(response, '查询当前用户');
}

/** 按表编码精确查询表定义（列表接口为等值匹配） */
export async function pageTableDefinitionsByCode(
  ctx: APIRequestContext,
  token: string,
  code: string,
): Promise<TableDefinitionRecord> {
  const response = await ctx.get(
    `${API_BASE_URL}/form/table-definition/page?current=1&size=50&code=${encodeURIComponent(code)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  const data = await readEnvelope<PageResult<TableDefinitionRecord>>(response, '查询表定义');
  const record = data.records.find((item) => item.tableCode === code);
  if (!record) {
    throw new Error(`表定义不存在：${code}`);
  }
  return record;
}

/** 表定义详情（含列元数据 id） */
export async function tableDefinitionDetail(
  ctx: APIRequestContext,
  token: string,
  id: string,
): Promise<TableDefinitionDetail> {
  const response = await ctx.get(`${API_BASE_URL}/form/table-definition/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readEnvelope<TableDefinitionDetail>(response, '查询表定义详情');
}

/**
 * 创建表单视图（元数据模型：tableDefinitionId + fields）。
 * 字段配置走 API 以绕开 @dnd-kit 拖拽（合成 PointerEvent 不可自动化的既有结论），
 * 保存语义与设计器 persist() 完全一致。
 */
export async function createFormDefinition(
  ctx: APIRequestContext,
  token: string,
  payload: Record<string, unknown>,
): Promise<FormDefinitionRecord> {
  const response = await ctx.post(`${API_BASE_URL}/form/definition`, {
    headers: { Authorization: `Bearer ${token}` },
    data: payload,
  });
  return readEnvelope<FormDefinitionRecord>(response, '创建表单视图');
}

/** 按表单编码查询数据分页 */
export async function pageFormData(
  ctx: APIRequestContext,
  token: string,
  definitionCode: string,
): Promise<PageResult<FormDataRecord>> {
  const response = await ctx.get(
    `${API_BASE_URL}/form/data/page?current=1&size=20&definitionCode=${encodeURIComponent(
      definitionCode,
    )}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  return readEnvelope<PageResult<FormDataRecord>>(response, '查询表单数据');
}

/** 尽力删除（清理阶段不因失败中断） */
export async function deleteResource(
  ctx: APIRequestContext,
  token: string,
  path: string,
): Promise<void> {
  try {
    await ctx.delete(`${API_BASE_URL}${path}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    // 清理失败不影响用例结论
  }
}

/** 工作流定义（仅取断言所需字段） */
export interface WorkflowDefinitionRecord {
  id: string;
  code: string;
}

/** 工作流任务（状态 0 待办 / 1 已同意 / 2 已驳回 / 3 已取消） */
export interface WorkflowTaskRecord {
  id: string;
  instanceId: string;
  nodeName?: string | null;
  nodeKey?: string | null;
  status?: number;
  signType?: string | null;
  signPolicy?: string | null;
}

/** 流程实例（状态 0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销） */
export interface WorkflowInstanceRecord {
  id: string;
  definitionId: string;
  definitionCode: string;
  title: string;
  status: number;
  currentNodeKey?: string | null;
  /** 仅详情接口填充 */
  tasks?: WorkflowTaskRecord[];
}

/** 按编码查询流程定义分页（编码精确匹配，空结果属正常） */
export async function pageWorkflowDefinitions(
  ctx: APIRequestContext,
  token: string,
  code?: string,
): Promise<PageResult<WorkflowDefinitionRecord>> {
  const query = code ? `&code=${encodeURIComponent(code)}` : '';
  const response = await ctx.get(
    `${API_BASE_URL}/workflow/definition/page?current=1&size=50${query}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  return readEnvelope<PageResult<WorkflowDefinitionRecord>>(response, '查询流程定义');
}

/** 创建流程定义（含规范化 JSON），返回定义记录 */
export async function createWorkflowDefinition(
  ctx: APIRequestContext,
  token: string,
  payload: Record<string, unknown>,
): Promise<WorkflowDefinitionRecord> {
  const response = await ctx.post(`${API_BASE_URL}/workflow/definition`, {
    headers: { Authorization: `Bearer ${token}` },
    data: payload,
  });
  return readEnvelope<WorkflowDefinitionRecord>(response, '创建流程定义');
}

/** 按标题模糊查询流程实例，返回首个命中记录 */
export async function findWorkflowInstanceByTitle(
  ctx: APIRequestContext,
  token: string,
  title: string,
): Promise<WorkflowInstanceRecord> {
  const response = await ctx.get(
    `${API_BASE_URL}/workflow/instance/page?current=1&size=20&title=${encodeURIComponent(title)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  const data = await readEnvelope<PageResult<WorkflowInstanceRecord>>(response, '查询流程实例');
  const record = data.records.find((item) => item.title === title);
  if (!record) {
    throw new Error(`流程实例不存在：${title}`);
  }
  return record;
}

/** 流程实例详情（含任务轨迹） */
export async function workflowInstanceDetail(
  ctx: APIRequestContext,
  token: string,
  id: string,
): Promise<WorkflowInstanceRecord> {
  const response = await ctx.get(`${API_BASE_URL}/workflow/instance/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readEnvelope<WorkflowInstanceRecord>(response, '查询流程实例详情');
}

/** 撤销运行中的流程实例（清理阶段使用，失败不抛错） */
export async function cancelWorkflowInstance(
  ctx: APIRequestContext,
  token: string,
  id: string,
): Promise<void> {
  try {
    await ctx.post(`${API_BASE_URL}/workflow/instance/${id}/cancel`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    // 清理失败不影响用例结论
  }
}
