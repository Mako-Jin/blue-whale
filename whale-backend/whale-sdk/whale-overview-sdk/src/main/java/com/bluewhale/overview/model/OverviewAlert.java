package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 一条告警（厅里的告警条、檐角的红黄灯）。
 *
 * <p>同类告警必须合并后再下发：{@code count} 表达条数，{@code text} 表达"是什么"。
 * 把 200 条告警原样塞进数组会让告警条滚成瀑布，在 3D 厅里根本读不了。</p>
 *
 * @param level 严重度
 * @param text 一句话说明，≤30 字
 * @param count 同类条数，可空（视为 1）
 * @param since 首现时刻 ISO-8601，可空
 * @author BlueWhale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewAlert(OverviewAlertLevel level, String text, Integer count, String since) {

    /**
     * 规范化：级别缺省警告，条数缺省 1
     *
     * @return 补齐缺省值后的告警
     */
    public OverviewAlert normalized() {
        return new OverviewAlert(level == null ? OverviewAlertLevel.WARNING : level, text, count, since);
    }
}
