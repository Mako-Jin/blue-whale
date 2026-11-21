package com.bluewhale.message.po.stat;

import lombok.Data;

/**
 * 时间窗内的投递统计（通政殿概览 T5.8）。
 *
 * <p>{@code bounced} 是 {@code failed} 的子集，单独列出来的原因是处置动作不同：
 * 失败可以重试，退信（地址无效/拒收）重试一百次也不会成功，只能改数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class MessageDeliveryStatPo {

    /** 投递总条数（按 sent_at，不含尚未投出的行） */
    private Long total;

    /** 成功条数：DELIVERED + READ */
    private Long success;

    /** 失败条数：FAILED + BOUNCED */
    private Long failed;

    /** 其中退信条数：BOUNCED */
    private Long bounced;
}
