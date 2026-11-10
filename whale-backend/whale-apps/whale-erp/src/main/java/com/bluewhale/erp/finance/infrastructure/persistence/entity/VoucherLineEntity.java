package com.bluewhale.erp.finance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 会计凭证行实体，对应表 {@code erp_fin_voucher_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_fin_voucher_line")
public class VoucherLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 凭证 ID */
    private Long voucherId;

    /** 行号 */
    private Integer lineNo;

    /** 科目编码 */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 摘要 */
    private String summary;

    /** 借方金额 */
    private BigDecimal debitAmount;

    /** 贷方金额 */
    private BigDecimal creditAmount;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;
}
