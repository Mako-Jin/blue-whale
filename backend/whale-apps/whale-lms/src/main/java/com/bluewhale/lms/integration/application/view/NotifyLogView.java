package com.bluewhale.lms.integration.application.view;

import com.bluewhale.lms.integration.domain.model.NotifyLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知投递日志视图。
 *
 * @author BlueWhale
 */
@Data
public class NotifyLogView {

    /** 主键 */
    private Long id;

    /** 通知场景 */
    private String scene;

    /** 幂等键 */
    private String idempotencyKey;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 接收人 */
    private Long userId;

    /** 消息模板编码 */
    private String templateCode;

    /** 通知标题 */
    private String title;

    /** 投递渠道 */
    private String channel;

    /** 投递状态 */
    private String sendStatus;

    /** 失败原因 */
    private String failReason;

    /** 投递时间 */
    private LocalDateTime sentAt;

    /**
     * 由领域对象构建视图。
     *
     * @param log 日志
     * @return 视图
     */
    public static NotifyLogView of(NotifyLog log) {
        NotifyLogView view = new NotifyLogView();
        view.setId(log.getId());
        view.setScene(log.getScene() == null ? null : log.getScene().name());
        view.setIdempotencyKey(log.getIdempotencyKey());
        view.setBizType(log.getBizType());
        view.setBizId(log.getBizId());
        view.setUserId(log.getUserId());
        view.setTemplateCode(log.getTemplateCode());
        view.setTitle(log.getTitle());
        view.setChannel(log.getChannel());
        view.setSendStatus(log.getSendStatus() == null ? null : log.getSendStatus().name());
        view.setFailReason(log.getFailReason());
        view.setSentAt(log.getSentAt());
        return view;
    }
}
