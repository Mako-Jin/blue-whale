package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作项关联实体，对应表 {@code pm_work_item_link}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_work_item_link")
public class PmWorkItemLink extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 源工作项ID */
    private Long sourceId;

    /** 目标工作项ID */
    private Long targetId;

    /** 关联类型：BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY */
    private String linkType;
}
