package com.bluewhale.mail.dto;

/**
 * 全文检索请求（服务内部使用，不直接暴露为 HTTP 入参）。
 *
 * @param accountId 邮箱账号ID（必填，检索范围先按账号收敛）
 * @param folderId 文件夹ID；{@code null} 表示跨文件夹检索
 * @param keyword 归一化后的检索词
 * @param limit 最多返回的命中数
 * @author BlueWhale
 */
public record MailSearchQuery(Long accountId, Long folderId, String keyword, int limit) {
}
