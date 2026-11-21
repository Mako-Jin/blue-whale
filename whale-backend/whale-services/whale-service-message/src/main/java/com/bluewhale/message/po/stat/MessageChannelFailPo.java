package com.bluewhale.message.po.stat;

import lombok.Data;

/**
 * 按渠道聚合的失败条数（通政殿概览 T5.8）。
 *
 * @author BlueWhale
 */
@Data
public class MessageChannelFailPo {

    /** 渠道：INAPP / EMAIL / SMS / IM */
    private String channel;

    /** 失败条数：FAILED + BOUNCED */
    private Long failures;
}
