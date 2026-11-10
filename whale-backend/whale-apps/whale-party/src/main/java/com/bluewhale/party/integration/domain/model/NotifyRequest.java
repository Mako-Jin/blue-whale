package com.bluewhale.party.integration.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.util.List;

/**
 * 一条待投递通知。
 *
 * @author BlueWhale
 */
@Data
public class NotifyRequest {

    /** 通知场景 */
    private NotifyScene scene;

    /** 幂等键 */
    private String idempotencyKey;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 消息模板编码 */
    private String templateCode;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 接收人 */
    private List<Long> recipients;

    /**
     * 校验必填项。
     */
    public void validate() {
        if (scene == null) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID, "通知场景不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID, "幂等键不能为空");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID, "通知接收人不能为空");
        }
    }
}
