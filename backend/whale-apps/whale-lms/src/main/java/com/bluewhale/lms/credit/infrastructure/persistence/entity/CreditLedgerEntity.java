package com.bluewhale.lms.credit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学分台账实体，对应表 {@code lr_credit_ledger}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_credit_ledger")
public class CreditLedgerEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 来源类型 */
    private String sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** 命中规则 ID */
    private Long ruleId;

    /** 命中规则编码快照 */
    private String ruleCode;

    /** 本次学分 */
    private BigDecimal credit;

    /** 业务发生时间 */
    private LocalDateTime occurredAt;

    /** 归属年度 */
    private Integer periodYear;

    /** 记账人 */
    private Long recordedBy;

    /** 备注 */
    private String remark;
}
