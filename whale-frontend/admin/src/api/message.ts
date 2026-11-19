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
  return get<PageResult<MessageTemplateVO>>('/message/v1/template/page', { ...params });
}

/** 新增模板 */
export function createMessageTemplate(body: MessageTemplateInput): Promise<MessageTemplateVO> {
  return post<MessageTemplateVO>('/message/v1/template', body);
}

/** 更新模板 */
export function updateMessageTemplate(
  id: string,
  body: MessageTemplateInput,
): Promise<MessageTemplateVO> {
  return put<MessageTemplateVO>(`/message/v1/template/${id}`, body);
}

/** 删除模板（逻辑删除） */
export function deleteMessageTemplate(id: string): Promise<void> {
  return del<void>(`/message/v1/template/${id}`);
}

// ---------------------------------------------------------------------------
// 系统公告（全员广播）：发布时不枚举接收人，用户收件箱读取时惰性展开
// 权限码：sys:message:announce:list/publish/revoke/delete（305–308）
// ---------------------------------------------------------------------------

/** 公告状态：已发布 / 已撤回 */
export type AnnouncementStatus = 'PUBLISHED' | 'REVOKED';

/** 系统公告（字段对齐后端 AnnouncementVO） */
export interface AnnouncementVO {
  /** 公告ID（后端 Long 序列化为字符串） */
  id: string;
  /** 关联的消息记录ID */
  recordId?: string;
  title?: string;
  content?: string;
  bizType?: string;
  status?: AnnouncementStatus;
  /** 发布人用户ID */
  publisherId?: string;
  publishTime?: string;
  createTime?: string;
}

/** 发布公告入参 */
export interface AnnouncementPublishInput {
  title: string;
  content: string;
  /** 业务类型，缺省后端按 SYSTEM_ANNOUNCE 处理 */
  bizType?: string;
}

/** 公告分页查询参数 */
export interface AnnouncementPageParams {
  status?: AnnouncementStatus;
  current: number;
  size: number;
}

/** 分页查询系统公告 */
export function pageAnnouncements(
  params: AnnouncementPageParams,
): Promise<PageResult<AnnouncementVO>> {
  return get<PageResult<AnnouncementVO>>('/message/v1/announce/page', { ...params });
}

/** 发布系统公告 */
export function publishAnnouncement(body: AnnouncementPublishInput): Promise<AnnouncementVO> {
  return post<AnnouncementVO>('/message/v1/announce', body);
}

/** 撤回公告：未展开用户不再可见，已展开的个人消息保留 */
export function revokeAnnouncement(id: string): Promise<void> {
  return put<void>(`/message/v1/announce/${id}/revoke`);
}

/** 删除公告（逻辑删除） */
export function deleteAnnouncement(id: string): Promise<void> {
  return del<void>(`/message/v1/announce/${id}`);
}
