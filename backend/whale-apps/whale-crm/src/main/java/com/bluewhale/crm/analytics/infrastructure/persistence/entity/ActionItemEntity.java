package com.bluewhale.crm.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行动项实体，对应表 {@code crm_action_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_action_item")
public class ActionItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 规则编码 */
    private String ruleCode;

    /** 关联对象类型 */
    private String refType;

    /** 关联对象 ID */
    private Long refId;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 标题 */
    private String title;

    /** 行动建议 */
    private String suggestion;

    /** 优先级 */
    private String priority;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 建议完成日期 */
    private LocalDate dueDate;

    /** 状态 */
    private String status;

    /** 关闭原因 */
    private String closeReason;

    /** 关闭人 */
    private Long closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 去重键 */
    private String dedupKey;
}
