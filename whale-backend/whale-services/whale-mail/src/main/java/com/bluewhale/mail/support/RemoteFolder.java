package com.bluewhale.mail.support;

/**
 * IMAP 服务器侧的文件夹快照。
 *
 * <p>只保留同步决策需要的三个事实：原始名称（含层级）、层级分隔符、是否可存邮件。
 * 展示名不在此计算——不同服务商的层级习惯差异很大，命名口径统一放到
 * {@code MailFolderServiceImpl}，避免两处各算一套。</p>
 *
 * @param imapName IMAP 原始名称（如 {@code INBOX.Work.2024}）
 * @param delimiter 层级分隔符（如 {@code .} 或 {@code /}；服务器可能返回 {@code null}）
 * @param selectable 是否可存邮件（{@code \Noselect} 的纯容器节点为 {@code false}）
 * @author BlueWhale
 */
public record RemoteFolder(String imapName, String delimiter, boolean selectable) {
}
