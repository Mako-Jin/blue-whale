package com.bluewhale.party.analytics.application.view;

import com.bluewhale.party.analytics.domain.model.MetricSnapshot;
import com.bluewhale.party.analytics.domain.model.MetricType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个指标视图（分子 / 分母 / 比率）。
 *
 * @author BlueWhale
 */
@Data
public class MetricItemView {

    /** 指标编码 */
    private String metricType;

    /** 指标名称 */
    private String label;

    /** 分子 */
    private BigDecimal numerator;

    /** 分母 */
    private BigDecimal denominator;

    /** 百分数比率（纯计数指标不展示） */
    private BigDecimal rate;

    /** 是否比率指标 */
    private boolean ratio;

    /**
     * 由快照转视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    public static MetricItemView from(MetricSnapshot snapshot) {
        MetricItemView view = new MetricItemView();
        view.setMetricType(snapshot.getMetricType());
        view.setLabel(MetricType.labelOf(snapshot.getMetricType()));
        view.setNumerator(snapshot.getNumerator());
        view.setDenominator(snapshot.getDenominator());
        view.setRate(snapshot.rate());
        view.setRatio(MetricType.isRatio(snapshot.getMetricType()));
        return view;
    }
}
