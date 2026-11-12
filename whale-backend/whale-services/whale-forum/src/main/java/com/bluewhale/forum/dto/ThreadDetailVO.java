package com.bluewhale.forum.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 帖子详情视图（列表项 + 正文 + 编辑窗口判定）。
 *
 * <p>{@code editable} 由服务端算好下发，前端不自己按时间推断——
 * 客户端时钟不可信，且审核版块内编辑会退回待审，规则只在服务端一处。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ThreadDetailVO extends ThreadListItemVO {

    private static final long serialVersionUID = 1L;

    /** 正文（已消毒富文本源码） */
    private String content;

    /** 标签 */
    private String tags;

    /** 下一个楼层号（前端可提示「即将是 N 楼」） */
    private Integer nextFloor;

    /** 当前用户是否可编辑（作者且在时间窗口内，或具备治理权限） */
    private Boolean editable;

    /** 当前用户是否可删除（作者或具备治理权限） */
    private Boolean deletable;

    /** 审核意见（驳回/退回时可见） */
    private String auditRemark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
