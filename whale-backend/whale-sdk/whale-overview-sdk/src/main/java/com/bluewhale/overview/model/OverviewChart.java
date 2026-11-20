package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 一张图表卡（折线 / 柱 / 环形）。
 *
 * @param key 厅内唯一键
 * @param title 图题
 * @param type 图形：line / bar / donut
 * @param format 刻度与图例的排版口径
 * @param categories 横轴标签或环形扇区名
 * @param series 数据序列；环形图只用第一条
 * @author BlueWhale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewChart(
        String key,
        String title,
        String type,
        OverviewFormat format,
        List<String> categories,
        List<OverviewSeries> series) {

    /**
     * 规范化：空列表安全化，格式缺省计数
     *
     * @return 补齐缺省值后的图表
     */
    public OverviewChart normalized() {
        List<String> safeCategories = categories == null ? List.of() : List.copyOf(categories);
        List<OverviewSeries> safeSeries = series == null
                ? List.of()
                : series.stream().map(OverviewSeries::normalized).toList();
        return new OverviewChart(
                key,
                title,
                type == null ? "line" : type,
                format == null ? OverviewFormat.COUNT : format,
                safeCategories,
                safeSeries);
    }

    /**
     * 判断横轴与各序列长度是否一致
     *
     * <p>不一致的图表在渲染层会错位（最后几个点落到不存在的刻度上），
     * 因此由服务端在组装时自检，宁可整张卡不下发也不要下发一张错位的图。</p>
     *
     * @return 长度全部一致且非空时返回 {@code true}
     */
    public boolean isAligned() {
        if (categories == null || categories.isEmpty() || series == null || series.isEmpty()) {
            return false;
        }
        int size = categories.size();
        return series.stream().allMatch(s -> s.values() != null && s.values().size() == size);
    }
}
