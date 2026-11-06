package com.bluewhale.party.integration.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知投递留痕。
 *
 * <p>一条记录 = 「某场景的某条通知发给某个人」的一次尝试；是否重复由
 * {@code (tenantId, idempotencyKey, userId)} 唯一键裁定，不依赖调用方自觉。</p>
 *
 * @author BlueWhale
 */
@Data
public class NotifyLog {

    /** 幂等键最大长度 */
    public static final int KEY_MAX_LENGTH = 128;

    /** 失败原因最大长度 */
    public static final int FAIL_REASON_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 通知场景 */
    private NotifyScene scene;

    /** 幂等键 */
    private String idempotencyKey;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 接收人用户 ID */
    private Long userId;

    /** 模板编码 */
    private String templateCode;

    /** 标题 */
    private String title;

    /** 渠道 */
    private String channel = "INAPP";

    /** 投递状态（刻意不给默认值，漏标状态不能变成「已发」） */
    private NotifyStatus sendStatus;

    /** 失败原因 */
    private String failReason;

    /** 投递时间 */
    private LocalDateTime sentAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 标记投递成功。
     *
     * @param sentAt 投递时间
     */
    public void markSent(LocalDateTime sentAt) {
        this.sendStatus = NotifyStatus.SENT;
        this.failReason = null;
        this.sentAt = sentAt == null ? LocalDateTime.now() : sentAt;
    }

    /**
     * 标记投递失败。
     *
     * @param reason 失败原因
     * @param sentAt 尝试时间
     */
    public void markFailed(String reason, LocalDateTime sentAt) {
        this.sendStatus = NotifyStatus.FAILED;
        this.failReason = reason == null ? null
                : reason.substring(0, Math.min(reason.length(), FAIL_REASON_MAX_LENGTH));
        this.sentAt = sentAt == null ? LocalDateTime.now() : sentAt;
    }

    /**
     * 是否已成功投递。
     *
     * @return 已发返回 true
     */
    public boolean alreadySent() {
        return sendStatus != null && sendStatus.isSent();
    }

    /**
     * 校验必填字段。
     */
    public void validate() {
        if (scene == null) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID, "通知场景不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID, "幂等键不能为空");
        }
        if (idempotencyKey.length() > KEY_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID,
                    "幂等键不得超过 " + KEY_MAX_LENGTH + " 字符");
        }
        if (userId == null) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID, "通知接收人不能为空");
        }
    }
}
