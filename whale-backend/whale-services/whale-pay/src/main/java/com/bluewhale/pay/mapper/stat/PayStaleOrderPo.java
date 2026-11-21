package com.bluewhale.pay.mapper.stat;

import lombok.Data;

/**
 * 停留在 {@code PAYING} 的支付单摘要（对账扫描用）。
 *
 * <p>扫描必须跨租户，而实体本身不映射 {@code tenant_id}，因此单独投影租户 ID：
 * 对账线程逐单还原租户上下文，保证后续流水与通知任务写回原租户。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayStaleOrderPo {

    /** 支付单 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;
}
