package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 状态流转配置视图对象
 *
 * @author BlueWhale
 */
@Data
public class TransitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 源状态ID */
    private String fromStatusId;

    /** 目标状态ID */
    private String toStatusId;

    /** 流转动作名称 */
    private String name;

    /** 是否要求填写评论 */
    private Boolean requireComment;

    /** 附加权限编码 */
    private String requirePermission;

    /** 创建时间 */
    private LocalDateTime createTime;
}
