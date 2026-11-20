package com.bluewhale.overview.model;

import java.util.List;

/**
 * 图表中的一条序列。
 *
 * <p>{@code values} 与 {@link OverviewChart#categories()} <b>必须等长</b>：
 * 缺数补 0，不要塞 {@code null} —— canvas 绘制时 null 会变成 NaN，整张卡静默变白，
 * 而"变白"在 3D 厅里和"数据为 0"长得一模一样。</p>
 *
 * @param name 序列名（图例）
 * @param values 与横轴标签一一对应的数值
 * @author BlueWhale
 */
public record OverviewSeries(String name, List<Double> values) {

    /**
     * 规范化：null 列表转空列表，null 值转 0
     *
     * @return 可安全绘制的序列
     */
    public OverviewSeries normalized() {
        if (values == null || values.isEmpty()) {
            return new OverviewSeries(name, List.of());
        }
        return new OverviewSeries(name, values.stream().map(v -> v == null ? 0d : v).toList());
    }
}
