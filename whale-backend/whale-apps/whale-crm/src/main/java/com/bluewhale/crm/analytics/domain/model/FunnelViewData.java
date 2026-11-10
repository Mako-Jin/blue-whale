package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售漏斗分析结果。
 *
 * <p>转化率口径为「下游阶段商机数 / 上游阶段商机数 × 100」。</p>
 *
 * @author BlueWhale
 */
@Data
public class FunnelViewData {

    /** 统计起始日 */
    private LocalDate startDate;

    /** 统计结束日 */
    private LocalDate endDate;

    /** 阶段数据（按阶段顺序） */
    private List<FunnelStage> stages;

    /** 输单数量 */
    private long lostCount;

    /** 输单金额 */
    private BigDecimal lostAmount;

    /** 漏斗入口商机数 */
    private long totalCount;

    /** 漏斗入口金额 */
    private BigDecimal totalAmount;
}
