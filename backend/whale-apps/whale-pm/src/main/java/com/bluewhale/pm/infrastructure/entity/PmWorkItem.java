package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工作项实体，对应表 {@code pm_work_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_work_item")
public class PmWorkItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 项目内序号 */
    private Integer itemNo;

    /** 工作项键（项目键-序号） */
    private String itemKey;

    /** 类型：EPIC / STORY / TASK / BUG / SUBTASK */
    private String type;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 状态ID */
    private Long statusId;

    /** 优先级：HIGHEST / HIGH / MEDIUM / LOW / LOWEST */
    private String priority;

    /** 负责人用户ID */
    private Long assigneeId;

    /** 报告人用户ID */
    private Long reporterId;

    /** 父工作项ID */
    private Long parentId;

    /** 迭代ID */
    private Long sprintId;

    /** 故事点 */
    private BigDecimal storyPoints;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 标签（英文逗号分隔） */
    private String labels;

    /** 排序值（12 位定长数字字符串） */
    private String rankValue;

    /** 扩展字段 JSON */
    private String extensionJson;

    /** 版本号（乐观锁） */
    private Integer version;

    /** 客户端请求ID（创建幂等键） */
    private String clientRequestId;
}
