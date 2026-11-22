package com.bluewhale.pay.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 手动触发对账请求。
 *
 * <p>{@code bizDate} 缺省为 T-1；{@code channel} 缺省或为 {@code ALL} 时，
 * 按当日实际发生交易的渠道逐个生成批次。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayReconcileRunRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对账业务日，缺省 T-1 */
    private LocalDate bizDate;

    /** 渠道编码；缺省或 ALL 表示当日全部渠道 */
    private String channel;
}
