package com.bluewhale.erp.finance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计凭证头实体，对应表 {@code erp_fin_voucher}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_fin_voucher")
public class VoucherEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 凭证号 */
    private String voucherNo;

    /** 凭证日期 */
    private LocalDate voucherDate;

    /** 期间编码 */
    private String periodCode;

    /** 凭证类型 */
    private String voucherType;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private String sourceId;

    /** 红冲的原凭证 ID */
    private Long reversalOfId;

    /** 被红冲凭证 ID */
    private Long reversedById;

    /** 备注 */
    private String remark;

    /** 记账时间 */
    private LocalDateTime postedAt;

    /** 记账人 */
    private Long postedBy;
}
