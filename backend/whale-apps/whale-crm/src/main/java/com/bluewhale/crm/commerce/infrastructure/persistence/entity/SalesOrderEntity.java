package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商务订单实体，对应表 {@code crm_sales_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_sales_order")
public class SalesOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID（后台跨租户扫描时显式携带） */
    private Long tenantId;

    /** 订单编号 */
    private String orderNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单据编号 */
    private String sourceOrderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 订单标题 */
    private String title;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 订单状态 */
    private String status;

    /** 审批实例 ID */
    private Long approvalInstanceId;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** ERP 订单号 */
    private String erpOrderNo;

    /** ERP 订单 ID */
    private Long erpOrderId;

    /** ERP 履约状态 */
    private String erpStatus;

    /** 下发成功时间 */
    private LocalDateTime pushedAt;

    /** 下发尝试次数 */
    private Integer pushAttempts;

    /** 最近失败原因（成功后需落 NULL，故更新策略固定写入） */
    @TableField(value = "last_push_error", updateStrategy = FieldStrategy.ALWAYS)
    private String lastPushError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer lockVersion;
}
