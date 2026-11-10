package com.bluewhale.doc.application.service;

import java.util.List;

/**
 * 文档站内消息事件（M3，事务提交后发送）。
 *
 * @param tenantId       租户ID
 * @param bizType        业务类型
 * @param bizId          业务标识
 * @param idempotencyKey 幂等键
 * @param title          消息标题
 * @param content        消息正文
 * @param recipientIds   接收人ID
 * @author BlueWhale
 */
public record DocNotifyEvent(Long tenantId, String bizType, String bizId, String idempotencyKey,
                             String title, String content, List<Long> recipientIds) {
}
