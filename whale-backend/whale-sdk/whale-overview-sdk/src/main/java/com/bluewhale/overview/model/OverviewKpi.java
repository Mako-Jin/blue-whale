package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 一个指标（厅里的 KPI 卡、牌坊上的一行字）。
 *
 * @param key 厅内唯一键，内容不变时不触发重绘
 * @param label 牌面文字，≤6 个汉字为佳
 * @param value 数值；百分比传比率（82% 记 0.82）
 * @param format 排版口径
 * @param unit 单位（元/件/秒）；口径已由 {@code format} 表达时留空
 * @param tone 语气，缺省中性
 * @param deltaPercent 环比比率（+12% 记 0.12），可空
 * @param hint 口径说明，≤20 字
 * @author BlueWhale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewKpi(
        String key,
        String label,
        double value,
        OverviewFormat format,
        String unit,
        OverviewTone tone,
        Double deltaPercent,
        String hint) {

    /**
     * 规范化可选字段：语气缺省中性，格式缺省计数
     *
     * @return 补齐缺省值后的指标
     */
    public OverviewKpi normalized() {
        return new OverviewKpi(
                key,
                label,
                value,
                format == null ? OverviewFormat.COUNT : format,
                unit,
                tone == null ? OverviewTone.FLAT : tone,
                deltaPercent,
                hint);
    }
}
