package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户应收快照（来自 ERP 只读契约，按客户按日幂等保存）。
 *
 * @author BlueWhale
 */
@Data
public class ArSnapshot {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户编码快照 */
    private String customerCode;

    /** 客户名称快照 */
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

    /** 逾期 1-30 天 */
    private BigDecimal overdue1To30;

    /** 逾期 31-60 天 */
    private BigDecimal overdue31To60;

    /** 逾期 61-90 天 */
    private BigDecimal overdue61To90;

    /** 逾期 90 天以上 */
    private BigDecimal overdueOver90;

    /** 数据来源 */
    private String source;

    /** 负责人（数据权限归属人） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
