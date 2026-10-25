package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作项视图对象（ID 一律字符串）
 *
 * @author BlueWhale
 */
@Data
public class WorkItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 项目内序号 */
    private Integer itemNo;

    /** 工作项键 */
    private String key;

    /** 类型 */
    private String type;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 状态ID */
    private String statusId;

    /** 状态编码 */
    private String statusCode;

    /** 状态名称 */
    private String statusName;

    /** 状态分类 */
    private String statusCategory;

    /** 优先级 */
    private String priority;

    /** 负责人用户ID */
    private String assigneeId;

    /** 报告人用户ID */
    private String reporterId;

    /** 父工作项ID */
    private String parentId;

    /** 迭代ID */
    private String sprintId;

    /** 故事点 */
    private BigDecimal storyPoints;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 标签 */
    private String labels;

    /** 排序值 */
    private String rankValue;

    /** 扩展字段 JSON */
    private String extensionJson;

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
