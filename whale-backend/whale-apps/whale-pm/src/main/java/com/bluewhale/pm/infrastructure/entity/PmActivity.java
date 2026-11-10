package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作项动态实体，对应表 {@code pm_activity}（append-only）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_activity")
public class PmActivity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 工作项ID（项目级动态可为空） */
    private Long workItemId;

    /** 操作人用户ID */
    private Long actorId;

    /** 动作：CREATE / UPDATE / TRANSITION / COMMENT / ATTACH / LINK / DELETE */
    private String action;

    /** 变更字段 */
    private String field;

    /** 旧值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 链路追踪ID */
    private String traceId;
}
