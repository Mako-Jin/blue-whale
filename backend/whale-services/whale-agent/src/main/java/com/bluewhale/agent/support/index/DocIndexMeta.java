package com.bluewhale.agent.support.index;

/**
 * 文档知识块元数据（写入 Lucene 存储字段，供检索结果回跳页面）。
 *
 * @param pageId  文档页面ID
 * @param spaceId 文档空间ID
 * @param url     页面地址（前端可直接跳转）
 * @author BlueWhale
 */
public record DocIndexMeta(Long pageId, Long spaceId, String url) {
}
