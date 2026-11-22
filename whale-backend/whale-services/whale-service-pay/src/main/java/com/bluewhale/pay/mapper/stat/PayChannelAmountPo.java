package com.bluewhale.pay.mapper.stat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按渠道聚合的收款额。
 *
 * @author BlueWhale
 */
@Data
public class PayChannelAmountPo {

    /** 渠道编码；历史数据可能为空，由调用方兜底成 UNKNOWN */
    private String channel;

    /** 收款笔数 */
    private Long orders;

    /** 收款金额 */
    private BigDecimal amount;
}
