package com.bluewhale.doc.application.service;

/**
 * 页面归档快照事件（M3，事务提交后入档）。
 *
 * @param tenantId 租户ID
 * @param pageId   页面ID
 * @param userId   操作人ID
 * @author BlueWhale
 */
public record DocArchiveEvent(Long tenantId, Long pageId, Long userId) {
}
