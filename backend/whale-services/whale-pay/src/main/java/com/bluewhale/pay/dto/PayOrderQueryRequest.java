package com.bluewhale.pay.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 支付单分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayOrderQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：支付单号 / 业务标识模糊匹配 */
    private String keyword;

    /** 状态（精确匹配）：CREATED / PAID / CANCELLED / REFUNDED */
    private String status;

    /** 支付渠道（精确匹配） */
    private String channel;
}
