package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批详情时间线任务视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalTimelineTaskView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 节点键 */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 审批人账号 */
    private String assignee;

    /** 任务归属人 */
    private String ownerId;

    /** 委派来源人 */
    private String delegateFrom;

    /** 状态 */
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 当前用户是否可操作 */
    private Boolean canOperate;
}
