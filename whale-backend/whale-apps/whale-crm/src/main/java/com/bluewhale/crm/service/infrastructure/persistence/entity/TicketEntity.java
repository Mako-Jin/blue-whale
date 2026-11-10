package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单实体，对应表 {@code crm_ticket}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_ticket")
public class TicketEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 工单号 */
    private String ticketNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名 */
    private String contactName;

    /** 合同台账 ID */
    private Long contractId;

    /** 商务订单 ID */
    private Long orderId;

    /** ERP 销售订单号 */
    private String erpOrderNo;

    /** 产品编码 */
    private String productCode;

    /** 序列号 */
    private String serialNo;

    /** 问题分类 */
    private String category;

    /** 优先级 */
    private String priority;

    /** 标题 */
    private String title;

    /** 问题描述 */
    private String description;

    /** 状态 */
    private String status;

    /** 处理人 ID */
    private Long assigneeId;

    /** 处理人姓名 */
    private String assigneeName;

    /** 负责人 ID */
    private Long ownerId;

    /** 部门 ID */
    private Long deptId;

    /** 响应截止时间 */
    private LocalDateTime responseDeadline;

    /** 解决截止时间 */
    private LocalDateTime resolveDeadline;

    /** 首次响应时间 */
    private LocalDateTime firstRespondedAt;

    /** 暂停开始时间 */
    private LocalDateTime pausedAt;

    /** 累计暂停秒数 */
    private Long pausedSeconds;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 解决方案 */
    private String solution;

    /** 重开次数 */
    private Integer reopenCount;

    /** 乐观锁版本 */
    private Integer lockVersion;
}
