package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户应收快照实体，对应表 {@code crm_ar_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_ar_snapshot")
public class ArSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 币种 */
    private String currency;

    /** 未结应收单数量 */
    private Integer receivableCount;

    /** 未结余额合计 */
    private BigDecimal totalRemaining;

    /** 未到期 */
    private BigDecimal notDue;

    /** 逾期 1-30 天（库列 overdue_1_30，数字段需显式映射） */
    @TableField("overdue_1_30")
    private BigDecimal overdue1To30;

    /** 逾期 31-60 天（库列 overdue_31_60） */
    @TableField("overdue_31_60")
    private BigDecimal overdue31To60;

    /** 逾期 61-90 天（库列 overdue_61_90） */
    @TableField("overdue_61_90")
    private BigDecimal overdue61To90;

    /** 逾期 90 天以上（库列 overdue_over_90） */
    @TableField("overdue_over_90")
    private BigDecimal overdueOver90;

    /** 数据来源 */
    private String source;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /** 备注 */
    private String remark;
}
