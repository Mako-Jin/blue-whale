package com.bluewhale.party.analytics.application.view;

import com.bluewhale.party.analytics.domain.model.MetricSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 趋势点（某一天的某个指标）。
 *
 * @author BlueWhale
 */
@Data
public class MetricTrendPointView {

    /** 统计日 */
    private LocalDate statDate;

    /** 分子 */
    private BigDecimal numerator;

    /** 分母 */
    private BigDecimal denominator;

    /** 百分数比率 */
    private BigDecimal rate;

    /**
     * 由快照转视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    public static MetricTrendPointView from(MetricSnapshot snapshot) {
        MetricTrendPointView view = new MetricTrendPointView();
        view.setStatDate(snapshot.getStatDate());
        view.setNumerator(snapshot.getNumerator());
        view.setDenominator(snapshot.getDenominator());
        view.setRate(snapshot.rate());
        return view;
    }
}
