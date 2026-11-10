package com.bluewhale.party.integration.application.view;

import com.bluewhale.party.integration.domain.model.NotifyLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知留痕视图（M-P4）。
 *
 * <p>只回标题与投递元数据，不回正文：正文里可能含党员姓名、金额等业务细节，
 * 排查投递问题不需要重新读一遍原文。</p>
 *
 * @author BlueWhale
 */
@Data
public class NotifyLogView {

    /** 主键 */
    private Long id;

    /** 通知场景 */
    private String scene;

    /** 场景中文标签 */
    private String sceneLabel;

    /** 幂等键 */
    private String idempotencyKey;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 接收人 */
    private Long userId;

    /** 标题 */
    private String title;

    /** 投递状态 */
    private String sendStatus;

    /** 投递状态中文标签 */
    private String sendStatusLabel;

    /** 失败原因 */
    private String failReason;

    /** 投递时间 */
    private LocalDateTime sentAt;

    /**
     * 由留痕转视图。
     *
     * @param log 留痕
     * @return 视图
     */
    public static NotifyLogView from(NotifyLog log) {
        NotifyLogView view = new NotifyLogView();
        view.setId(log.getId());
        view.setScene(log.getScene() == null ? null : log.getScene().name());
        view.setSceneLabel(log.getScene() == null ? null : log.getScene().getDefaultTitle());
        view.setIdempotencyKey(log.getIdempotencyKey());
        view.setBizType(log.getBizType());
        view.setBizId(log.getBizId());
        view.setUserId(log.getUserId());
        view.setTitle(log.getTitle());
        view.setSendStatus(log.getSendStatus() == null ? null : log.getSendStatus().name());
        view.setSendStatusLabel(log.getSendStatus() == null ? null : log.getSendStatus().getLabel());
        view.setFailReason(log.getFailReason());
        view.setSentAt(log.getSentAt());
        return view;
    }
}
