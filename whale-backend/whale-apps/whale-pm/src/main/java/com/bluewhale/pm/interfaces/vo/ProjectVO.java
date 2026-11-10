package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目空间视图对象（ID 一律字符串，避免前端精度丢失）
 *
 * @author BlueWhale
 */
@Data
public class ProjectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目键 */
    private String key;

    /** 项目名称 */
    private String name;

    /** 项目类型：SCRUM / KANBAN */
    private String projectType;

    /** 可见性：PRIVATE / TEAM */
    private String visibility;

    /** 项目负责人用户ID */
    private String leadUserId;

    /** 项目描述 */
    private String description;

    /** 项目状态：ACTIVE / ARCHIVED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
