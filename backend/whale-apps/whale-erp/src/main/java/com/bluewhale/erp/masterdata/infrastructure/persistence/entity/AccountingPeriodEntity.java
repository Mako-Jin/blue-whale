package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计期间实体，对应表 {@code erp_accounting_period}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_accounting_period")
public class AccountingPeriodEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 期间编码 yyyyMM */
    private String periodCode;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态 */
    private String status;

    /** 关账时间 */
    private LocalDateTime closedAt;

    /** 关账人 */
    private Long closedBy;

    /** 备注 */
    private String remark;
}
