package com.bluewhale.lms.integration.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.util.List;

/**
 * 一条待投递的通知（领域侧入参）。
 *
 * <p>只表达「发给谁、发什么、凭什么幂等」，不关心渠道与 HTTP 细节，
 * 由基础设施层的网关负责落到消息服务。</p>
 *
 * @author BlueWhale
 */
@Data
public class NotifyRequest {

    /** 通知场景 */
    private NotifyScene scene;

    /** 幂等键（同一键同一接收人只发一次） */
    private String idempotencyKey;

    /** 业务类型（冗余检索） */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 消息模板编码 */
    private String templateCode;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 接收人列表 */
    private List<Long> recipients;

    /**
     * 校验必填项。
     */
    public void validate() {
        if (scene == null) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "通知场景不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "幂等键不能为空");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "接收人不能为空");
        }
    }
}
