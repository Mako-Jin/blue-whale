package com.bluewhale.lms.credit.application.view;

import com.bluewhale.lms.credit.domain.model.CreditBucket;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 学分域指标（看板取数用）。
 *
 * @author BlueWhale
 */
@Data
public class CreditMetricsView {

    /** 学分总量 */
    private BigDecimal totalCredit = BigDecimal.ZERO;

    /** 获学分人数 */
    private long earners;

    /** 按人合计学分的分布 */
    private List<CreditBucket> distribution = new ArrayList<>();
}
