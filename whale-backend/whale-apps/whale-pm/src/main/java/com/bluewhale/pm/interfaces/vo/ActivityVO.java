package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作项动态视图对象
 *
 * @author BlueWhale
 */
@Data
public class ActivityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 工作项ID */
    private String workItemId;

    /** 操作人用户ID */
    private String actorId;

    /** 动作 */
    private String action;

    /** 变更字段 */
    private String field;

    /** 旧值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 链路追踪ID */
    private String traceId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
