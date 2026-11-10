package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目空间实体，对应表 {@code pm_project}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_project")
public class PmProject extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目键（租户内唯一，大写字母开头，如 BW） */
    private String projectKey;

    /** 项目名称 */
    private String name;

    /** 项目类型：SCRUM / KANBAN */
    private String projectType;

    /** 可见性：PRIVATE / TEAM */
    private String visibility;

    /** 项目负责人用户ID */
    private Long leadUserId;

    /** 项目描述 */
    private String description;

    /** 项目状态：ACTIVE / ARCHIVED */
    private String status;
}
