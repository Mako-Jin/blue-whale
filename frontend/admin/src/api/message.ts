// 消息服务 API（经网关 → whale-message /message）
// 权限码：sys:message:template:list/add/edit/delete（239–242）

import { del, get, post, put, type PageResult } from './client';

/** 消息模板（字段对齐后端 MessageTemplateVO） */
export interface MessageTemplateVO {
  /** 后端 Long 序列化为字符串 */
  id: string;
  /** 模板编码（租户内唯一，大写） */
  templateCode: string;
  templateName: string;
  /** 标题模板，支持 ${key} 占位符 */
  titleTemplate?: string;
  /** 内容模板，支持 ${key} 占位符 */
  contentTemplate?: string;
  /** 1 启用 / 0 停用 */
  status?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 模板新增/编辑入参（编辑时携带 id） */
export interface MessageTemplateInput {
  id?: string;
  templateCode: string;
  templateName: string;
  titleTemplate?: string;
  contentTemplate?: string;
  status?: number;
  remark?: string;
}

/** 模板分页查询参数 */
export interface MessageTemplatePageParams {
  templateCode?: string;
  templateName?: string;
  status?: number;
  current: number;
  size: number;
}

/** 分页查询模板 */
export function pageMessageTemplates(
  params: MessageTemplatePageParams,
): Promise<PageResult<MessageTemplateVO>> {
  return get<PageResult<MessageTemplateVO>>('/message/template/page', { ...params });
}

/** 新增模板 */
export function createMessageTemplate(body: MessageTemplateInput): Promise<MessageTemplateVO> {
  return post<MessageTemplateVO>('/message/template', body);
}

/** 更新模板 */
export function updateMessageTemplate(
  id: string,
  body: MessageTemplateInput,
): Promise<MessageTemplateVO> {
  return put<MessageTemplateVO>(`/message/template/${id}`, body);
}

/** 删除模板（逻辑删除） */
export function deleteMessageTemplate(id: string): Promise<void> {
  return del<void>(`/message/template/${id}`);
}
