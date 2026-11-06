package com.bluewhale.lms.integration.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知投递留痕。
 *
 * <p>一条记录 = 一次「某场景的某条通知发给某个人」的尝试。是否重复由
 * {@code (idempotencyKey, userId)} 唯一键裁定，不依赖调用方自觉。</p>
 *
 * <p>失败记录保留而不是丢弃：{@link NotifyStatus#FAILED} 可被下一轮重试
 * 覆盖为 {@link NotifyStatus#SENT}，{@code failReason} 则留存最后一次失败原因，
 * 便于回答「为什么没发出去」。</p>
 *
 * @author BlueWhale
 */
@Data
public class NotifyLog {

    /** 幂等键最大长度（与列宽一致） */
    private static final int KEY_MAX_LENGTH = 128;

    /** 失败原因最大长度（与列宽一致） */
    private static final int FAIL_REASON_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 通知场景 */
    private NotifyScene scene;

    /** 幂等键（场景内唯一） */
    private String idempotencyKey;

    /** 业务类型（冗余检索） */
    private String bizType;

    /** 业务 ID（冗余检索） */
    private Long bizId;

    /** 接收人用户 ID */
    private Long userId;

    /** 消息模板编码 */
    private String templateCode;

    /** 通知标题 */
    private String title;

    /** 投递渠道 */
    private String channel = "INAPP";

    /**
     * 投递状态。
     *
     * <p>刻意不给默认值：新建对象代表「还没发过」，默认成 {@code SENT}
     * 会让任何漏调 {@code markSent/markFailed} 的路径静默变成「已发、不重发」。</p>
     */
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
     * 标记为投递成功。
     *
     * @param sentAt 投递时间
     */
    public void markSent(LocalDateTime sentAt) {
        this.sendStatus = NotifyStatus.SENT;
        this.failReason = null;
        this.sentAt = sentAt == null ? LocalDateTime.now() : sentAt;
    }

    /**
     * 标记为投递失败。
     *
     * @param reason 失败原因（超长截断，避免撑爆列宽）
     * @param sentAt 尝试时间
     */
    public void markFailed(String reason, LocalDateTime sentAt) {
        this.sendStatus = NotifyStatus.FAILED;
        this.failReason = reason == null ? null
                : reason.substring(0, Math.min(reason.length(), FAIL_REASON_MAX_LENGTH));
        this.sentAt = sentAt == null ? LocalDateTime.now() : sentAt;
    }

    /**
     * 是否已成功投递（成功则本轮不应再发）。
     *
     * @return 已发返回 {@code true}
     */
    public boolean alreadySent() {
        return sendStatus != null && sendStatus.isSent();
    }

    /**
     * 校验必填字段。
     */
    public void validate() {
        if (scene == null) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "通知场景不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "幂等键不能为空");
        }
        if (idempotencyKey.length() > KEY_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID,
                    "幂等键不得超过 " + KEY_MAX_LENGTH + " 字符");
        }
        if (userId == null) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "接收人不能为空");
        }
    }
}
