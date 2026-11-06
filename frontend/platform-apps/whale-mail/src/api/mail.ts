// 邮箱接口封装（M0：账号 / 文件夹 / 邮件 / 发信 / 草稿 / 同步）。
//
// 权限码与 migration-5.89-mail.sql 对齐：
//   sys:mail:account:list/add/edit/remove（2100–2103）、
//   sys:mail:message:list/edit/send/remove（2104/2105/2106/2107）、
//   sys:mail:sync（2108）。
// 路径前缀 `/mail` 由网关转发，网关不剥离一级前缀（REQ-ARCH-003）。

import { downloadBlob, http, uploadForm, type DownloadResult, type PageResult } from './client';

/** 文件夹编码（与后端 MailFolderType 对齐） */
export type FolderCode = 'INBOX' | 'SENT' | 'DRAFT' | 'TRASH' | 'JUNK' | 'CUSTOM';

/** 邮箱账号视图（后端刻意不返回凭据引用） */
export interface MailAccount {
  id: number;
  email: string;
  displayName?: string | null;
  domain?: string | null;
  accountType?: string | null;
  status?: number | null;
  syncEnabled?: number | null;
  imapHost?: string | null;
  imapPort?: number | null;
  smtpHost?: string | null;
  smtpPort?: number | null;
  quotaBytes?: number | null;
  usedBytes?: number | null;
  lastSyncTime?: string | null;
}

/** 账号保存命令 */
export interface MailAccountSaveCommand {
  email: string;
  displayName?: string;
  accountType?: string;
  secretRef?: string;
  imapHost?: string;
  imapPort?: number;
  imapSsl?: boolean;
  smtpHost?: string;
  smtpPort?: number;
  smtpSsl?: boolean;
  syncEnabled?: boolean;
}

/** 邮箱文件夹 */
export interface MailFolder {
  id: number;
  folderCode: string;
  name: string;
  folderType: string;
  unreadCount: number;
  totalCount: number;
}

/** 邮件列表项 / 详情共用结构（正文仅详情返回） */
export interface MailMessage {
  id: number;
  folderId?: number | null;
  folderCode?: string | null;
  subject?: string | null;
  fromAddr?: string | null;
  fromName?: string | null;
  toAddrs?: string | null;
  ccAddrs?: string | null;
  bccAddrs?: string | null;
  sentTime?: string | null;
  sizeBytes?: number | null;
  hasAttachment?: number | null;
  seen?: number | null;
  flagged?: number | null;
  preview?: string | null;
  bodyText?: string | null;
  bodyHtml?: string | null;
  attachments?: MailAttachment[];
  /** 退信回执明细：非空表示这封是退信通知（仅详情返回） */
  bounces?: MailBounce[];
}

/** 退信回执（一条对应一个被退回的收件人） */
export interface MailBounce {
  id: number;
  notifyMessageId: number;
  originalMessageId?: number | null;
  originalRfcId?: string | null;
  recipient: string;
  action?: string | null;
  statusCode?: string | null;
  diagnostic?: string | null;
  /** HARD 永久失败 / SOFT 临时失败 / UNKNOWN */
  bounceType?: string | null;
  reportingMta?: string | null;
  remoteMta?: string | null;
  createTime?: string | null;
}

/** 邮件附件 */
export interface MailAttachment {
  id: number;
  fileId?: number | null;
  fileName?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  inlineFlag?: number | null;
  contentId?: string | null;
}

/** 未读统计 */
export interface MailUnread {
  total: number;
  byFolder: Record<string, number>;
}

/** 邮件分页查询条件 */
export interface MailMessageQuery {
  accountId?: number;
  folderId?: number;
  folderCode?: string;
  keyword?: string;
  /** 检索范围：FOLDER 当前文件夹（默认）/ ALL 全部文件夹 */
  scope?: 'FOLDER' | 'ALL';
  seen?: number;
  hasAttachment?: number;
  current?: number;
  size?: number;
}

/** 发信命令 */
export interface MailSendCommand {
  accountId: number;
  to: string[];
  cc?: string[];
  bcc?: string[];
  subject?: string;
  bodyHtml?: string;
  bodyText?: string;
  attachments?: MailAttachmentRef[];
}

/** 附件引用（二进制已在 whale-file，发信只传引用） */
export interface MailAttachmentRef {
  fileId: number;
  fileName: string;
  contentType?: string;
  sizeBytes?: number;
}

/** whale-file 上传结果（字段与 `/file/upload` 的 FileUploadVO 对齐） */
export interface UploadedFile {
  id: number;
  fileName?: string | null;
  originalName?: string | null;
  fileSize?: number | null;
  contentType?: string | null;
}

/** 草稿 */
export interface MailDraft {
  id: number;
  accountId: number;
  draftKey?: string | null;
  subject?: string | null;
  toAddrs?: string | null;
  ccAddrs?: string | null;
  bccAddrs?: string | null;
  bodyHtml?: string | null;
  bodyText?: string | null;
  updateTime?: string | null;
}

/** 草稿保存命令 */
export interface MailDraftSaveCommand {
  accountId: number;
  draftKey?: string;
  subject?: string;
  to?: string[];
  cc?: string[];
  bcc?: string[];
  bodyHtml?: string;
  bodyText?: string;
}

// ---------------------------------------------------------------------------
// 账号
// ---------------------------------------------------------------------------

/** 当前用户可见的邮箱账号列表 */
export function listAccounts(): Promise<MailAccount[]> {
  return http.get<MailAccount[]>('/mail/account/list');
}

/** 当前用户默认邮箱账号（无绑定时抛出业务错误） */
export function currentAccount(): Promise<MailAccount> {
  return http.get<MailAccount>('/mail/account/current');
}

/** 绑定邮箱账号 */
export function createAccount(body: MailAccountSaveCommand): Promise<MailAccount> {
  return http.post<MailAccount>('/mail/account', body);
}

/** 修改邮箱账号 */
export function updateAccount(id: number, body: MailAccountSaveCommand): Promise<MailAccount> {
  return http.put<MailAccount>(`/mail/account/${id}`, body);
}

/** 解绑邮箱账号 */
export function removeAccount(id: number): Promise<void> {
  return http.del<void>(`/mail/account/${id}`);
}

// ---------------------------------------------------------------------------
// 文件夹与未读
// ---------------------------------------------------------------------------

/** 文件夹列表（含未读与总数） */
export function listFolders(accountId?: number): Promise<MailFolder[]> {
  return http.get<MailFolder[]>('/mail/folder/list', { accountId });
}

/** 文件夹与服务器对齐的结果统计 */
export interface MailFolderSyncResult {
  added: number;
  updated: number;
  removed: number;
  remote: number;
  total: number;
}

/** 立即探测服务器文件夹（用户在 Webmail 新建目录后无需等定时同步） */
export function refreshFolders(accountId?: number): Promise<MailFolderSyncResult> {
  const query = accountId ? `?accountId=${accountId}` : '';
  return http.post<MailFolderSyncResult>(`/mail/folder/refresh${query}`);
}

/** 未读统计 */
export function getUnread(): Promise<MailUnread> {
  return http.get<MailUnread>('/mail/message/unread');
}

// ---------------------------------------------------------------------------
// 邮件
// ---------------------------------------------------------------------------

/** 邮件分页 */
export function pageMessages(query: MailMessageQuery): Promise<PageResult<MailMessage>> {
  return http.get<PageResult<MailMessage>>('/mail/message/page', { ...query });
}

/** 检索引擎状态（决定是否显示「重建索引」） */
export interface MailSearchStatus {
  engine: string;
  externalIndex: boolean;
  maxHits: number;
  likeFallback: boolean;
}

/** 查看检索引擎状态 */
export function getSearchStatus(): Promise<MailSearchStatus> {
  return http.get<MailSearchStatus>('/mail/search/status');
}

/** 重建当前账号的检索索引（MySQL 引擎返回 0） */
export function reindexSearch(accountId?: number): Promise<number> {
  const query = accountId ? `?accountId=${accountId}` : '';
  return http.post<number>(`/mail/search/reindex${query}`);
}

/** 邮件详情（正文惰性回源，首次打开可能稍慢） */
export function getMessage(id: number): Promise<MailMessage> {
  return http.get<MailMessage>(`/mail/message/${id}`);
}

/** 标记已读 / 未读 */
export function markSeen(id: number, seen: boolean): Promise<void> {
  return http.put<void>(`/mail/message/${id}/seen?seen=${seen ? 1 : 0}`);
}

/** 标记星标 */
export function markFlagged(id: number, flagged: boolean): Promise<void> {
  return http.put<void>(`/mail/message/${id}/flagged?flagged=${flagged ? 1 : 0}`);
}

/** 移入回收站 */
export function removeMessage(id: number): Promise<void> {
  return http.del<void>(`/mail/message/${id}`);
}

// ---------------------------------------------------------------------------
// 发信与草稿
// ---------------------------------------------------------------------------

/** 发送邮件（M1 支持附件，附件需先经 {@link uploadAttachment} 上传换取 fileId） */
export function sendMail(body: MailSendCommand): Promise<void> {
  return http.post<void>('/mail/send', body);
}

/**
 * 上传附件到文件服务，返回文件引用。
 *
 * <p>附件先传再发：SMTP 阶段只做「取流 + 组装」，避免把 HTTP 上传与 SMTP 事务
 * 串在一个请求里，超时后既不知道邮件发没发出去、也不知道文件传没传完。</p>
 *
 * @param file 浏览器文件对象
 * @returns 上传结果
 */
export function uploadAttachment(file: File): Promise<UploadedFile> {
  return uploadForm<UploadedFile>('/file/upload', file);
}

/**
 * 下载附件字节。
 *
 * <p>必须走带 token 的请求通道：直接开新窗口访问下载地址不会带上 Authorization，
 * 而后端按「邮件 → 账号 → 当前用户」校验归属。</p>
 *
 * @param attachmentId 附件ID
 * @returns 字节流与文件名
 */
export function downloadAttachment(attachmentId: number): Promise<DownloadResult> {
  return downloadBlob(`/mail/attachment/${attachmentId}/download`);
}

/** 草稿分页 */
export function pageDrafts(query: {
  current?: number;
  size?: number;
  accountId?: number;
}): Promise<PageResult<MailDraft>> {
  return http.get<PageResult<MailDraft>>('/mail/draft/page', { ...query });
}

/** 新建草稿 */
export function createDraft(body: MailDraftSaveCommand): Promise<MailDraft> {
  return http.post<MailDraft>('/mail/draft', body);
}

/** 更新草稿 */
export function updateDraft(id: number, body: MailDraftSaveCommand): Promise<MailDraft> {
  return http.put<MailDraft>(`/mail/draft/${id}`, body);
}

/** 删除草稿 */
export function removeDraft(id: number): Promise<void> {
  return http.del<void>(`/mail/draft/${id}`);
}

// ---------------------------------------------------------------------------
// 同步
// ---------------------------------------------------------------------------

/** 手动同步指定账号，返回本次新增邮件数 */
export function syncAccount(accountId: number): Promise<number> {
  return http.post<number>(`/mail/sync/${accountId}`);
}

// ---------------------------------------------------------------------------
// 收信规则
// ---------------------------------------------------------------------------

/** 收信规则 */
export interface MailRule {
  id: number;
  name: string;
  sortNo: number;
  enabled: boolean;
  matchFrom?: string | null;
  matchSubject?: string | null;
  matchTo?: string | null;
  onlyWithAttachment?: boolean;
  actionFolderId?: number | null;
  actionFolderName?: string | null;
  actionMarkSeen?: boolean;
  actionFlagged?: boolean;
  hitCount: number;
  lastHitTime?: string | null;
}

/** 规则保存命令 */
export interface MailRuleSaveCommand {
  id?: number;
  accountId?: number;
  name: string;
  sortNo?: number;
  enabled?: boolean;
  matchFrom?: string;
  matchSubject?: string;
  matchTo?: string;
  onlyWithAttachment?: boolean;
  actionFolderId?: number | null;
  actionMarkSeen?: boolean;
  actionFlagged?: boolean;
}

/** 规则回填结果 */
export interface MailRuleApplyResult {
  scanned: number;
  changed: number;
  truncated: boolean;
}

/** 规则列表（按优先级升序，即执行顺序） */
export function listRules(accountId?: number): Promise<MailRule[]> {
  return http.get<MailRule[]>('/mail/rule/list', { accountId });
}

/** 新建或更新规则 */
export function saveRule(body: MailRuleSaveCommand): Promise<MailRule> {
  return http.post<MailRule>('/mail/rule/save', body);
}

/** 启用 / 停用规则 */
export function setRuleEnabled(id: number, enabled: boolean): Promise<void> {
  return http.post<void>(`/mail/rule/enabled?id=${id}&enabled=${enabled}`);
}

/** 删除规则 */
export function removeRule(id: number): Promise<void> {
  return http.post<void>(`/mail/rule/remove?id=${id}`);
}

/** 对收件箱存量邮件回填规则 */
export function applyRules(accountId?: number): Promise<MailRuleApplyResult> {
  const suffix = accountId ? `?accountId=${accountId}` : '';
  return http.post<MailRuleApplyResult>(`/mail/rule/apply${suffix}`);
}
