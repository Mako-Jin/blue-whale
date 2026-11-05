package com.bluewhale.pdm.change.client;

import java.util.List;

/**
 * 变更通知消息客户端
 *
 * <p>投递归 whale-message；PDM 只记录投递日志，失败时可重试。</p>
 *
 * @author BlueWhale
 */
public interface PdmChgMessageClient {

    /**
     * 发送站内消息。
     *
     * @param tenantId       租户ID
     * @param bizType        业务类型
     * @param bizId          业务标识
     * @param idempotencyKey 幂等键
     * @param title          标题
     * @param content        正文
     * @param channels       渠道
     * @param userIds        接收人
     */
    void send(Long tenantId, String bizType, String bizId, String idempotencyKey,
              String title, String content, List<String> channels, List<Long> userIds);
}
