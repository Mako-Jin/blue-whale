package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证视图（含分录）。
 *
 * @author BlueWhale
 */
@Data
public class VoucherView {

    /** 主键 */
    private Long id;

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

    /** 分录 */
    private List<VoucherLineView> lines;
}
