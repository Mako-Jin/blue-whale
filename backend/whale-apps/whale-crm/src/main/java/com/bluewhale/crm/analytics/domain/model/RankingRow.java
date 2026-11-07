package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售排行行。
 *
 * <p>回款率 = (合同金额 − 最新应收未结余额) / 合同金额 × 100；合同金额为 0 时按 0 处理。</p>
 *
 * @author BlueWhale
 */
@Data
public class RankingRow {

    /** 排名 */
    private int rank;

    /** 负责人 ID */
    private Long ownerId;

    /** 负责人名称 */
    private String ownerName;

    /** 赢单金额 */
    private BigDecimal wonAmount;

    /** 赢单数量 */
    private long wonCount;

    /** 合同数量 */
    private long contractCount;

    /** 回款率（百分比） */
    private BigDecimal collectionRate;
}
