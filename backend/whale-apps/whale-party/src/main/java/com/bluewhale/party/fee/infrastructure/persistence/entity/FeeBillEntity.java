package com.bluewhale.party.fee.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 党费账单实体，对应表 {@code pty_fee_bill}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_fee_bill")
public class FeeBillEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 党员档案 ID */
    private Long memberId;

    /** 支部 ID */
    private Long branchId;

    /** 党员姓名快照 */
    private String memberName;

    /** 所属月份 yyyyMM */
    private String periodMonth;

    /** 党费基数（生成时快照） */
    private BigDecimal duesBase;

    /** 缴费比例（生成时快照） */
    private BigDecimal rate;

    /** 应缴金额 */
    private BigDecimal dueAmount;

    /** 实缴金额 */
    private BigDecimal paidAmount;

    /** 最近缴费日期 */
    private LocalDate paidDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
