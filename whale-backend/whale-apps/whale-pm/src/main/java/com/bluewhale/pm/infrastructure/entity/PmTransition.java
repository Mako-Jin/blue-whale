package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 状态流转配置实体，对应表 {@code pm_transition}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_transition")
public class PmTransition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 源状态ID */
    private Long fromStatusId;

    /** 目标状态ID */
    private Long toStatusId;

    /** 流转动作名称 */
    private String name;

    /** 是否要求填写评论：1 是 / 0 否 */
    private Integer requireComment;

    /** 附加权限编码（可选） */
    private String requirePermission;
}
