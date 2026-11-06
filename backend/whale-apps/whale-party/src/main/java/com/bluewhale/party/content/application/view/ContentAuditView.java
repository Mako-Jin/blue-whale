package com.bluewhale.party.content.application.view;

import com.bluewhale.party.content.domain.model.AuditAction;
import com.bluewhale.party.content.domain.model.ContentAudit;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 党建内容审核轨迹视图。
 *
 * @author BlueWhale
 */
@Data
public class ContentAuditView {

    /** 轨迹 ID */
    private Long id;

    /** 内容 ID */
    private Long contentId;

    /** 动作码 */
    private String action;

    /** 动作标签 */
    private String actionLabel;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 审核意见 */
    private String opinion;

    /** 操作人用户 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param audit 审核轨迹
     * @return 视图
     */
    public static ContentAuditView from(ContentAudit audit) {
        ContentAuditView view = new ContentAuditView();
        view.setId(audit.getId());
        view.setContentId(audit.getContentId());
        AuditAction action = audit.getAction() == null ? AuditAction.SUBMIT : audit.getAction();
        view.setAction(action.name());
        view.setActionLabel(action.getLabel());
        view.setFromStatus(audit.getFromStatus());
        view.setToStatus(audit.getToStatus());
        view.setOpinion(audit.getOpinion());
        view.setOperatorId(audit.getOperatorId());
        view.setOperatorName(audit.getOperatorName());
        view.setCreateTime(audit.getCreateTime());
        return view;
    }
}
