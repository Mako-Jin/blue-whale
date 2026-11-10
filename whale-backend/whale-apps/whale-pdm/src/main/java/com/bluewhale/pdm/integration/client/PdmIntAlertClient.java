package com.bluewhale.pdm.integration.client;

import java.util.List;

/**
 * PDM 集成告警客户端
 *
 * <p>投递归 whale-message；告警失败只记日志，不影响同步结果。</p>
 *
 * @author BlueWhale
 */
public interface PdmIntAlertClient {

    /**
     * 发送站内告警。
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
