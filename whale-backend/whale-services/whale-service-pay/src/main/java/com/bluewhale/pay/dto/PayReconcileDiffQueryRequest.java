package com.bluewhale.pay.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 对账差异分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayReconcileDiffQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 批次 ID */
    private Long batchId;

    /** 渠道编码（精确匹配） */
    private String channel;

    /** 差异类型：LOCAL_ONLY / CHANNEL_ONLY / STATUS_MISMATCH / AMOUNT_MISMATCH */
    private String diffType;

    /** 处置状态：OPEN / CONFIRMED / COMPENSATED / SUSPENDED */
    private String handleStatus;

    /** 对账业务日 */
    private LocalDate bizDate;
}
