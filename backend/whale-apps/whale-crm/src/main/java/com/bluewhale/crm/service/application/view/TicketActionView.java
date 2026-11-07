package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.TicketAction;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单处理记录视图。
 *
 * @author BlueWhale
 */
@Data
public class TicketActionView {

    /** 记录 ID */
    private Long id;

    /** 动作类型 */
    private String actionType;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 内容 */
    private String content;

    /** 记录时间 */
    private LocalDateTime createdAt;

    /**
     * 由领域对象构建视图。
     *
     * @param action 处理记录
     * @return 视图
     */
    public static TicketActionView from(TicketAction action) {
        TicketActionView view = new TicketActionView();
        view.setId(action.getId());
        view.setActionType(action.getActionType() == null ? null : action.getActionType().name());
        view.setFromStatus(action.getFromStatus() == null ? null : action.getFromStatus().name());
        view.setToStatus(action.getToStatus() == null ? null : action.getToStatus().name());
        view.setOperatorId(action.getOperatorId());
        view.setOperatorName(action.getOperatorName());
        view.setContent(action.getContent());
        view.setCreatedAt(action.getCreatedAt());
        return view;
    }
}
