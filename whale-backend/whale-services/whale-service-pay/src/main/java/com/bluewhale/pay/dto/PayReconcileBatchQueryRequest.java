package com.bluewhale.pay.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 对账批次分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayReconcileBatchQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 渠道编码（精确匹配） */
    private String channel;

    /** 批次状态：RUNNING / SUCCESS / FAILED / PARTIAL */
    private String status;

    /** 对账业务日 */
    private LocalDate bizDate;
}
