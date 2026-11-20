package com.bluewhale.monitor.overview;

import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.support.Overviews;

/**
 * 一次下游问询的结果（总控内部的中间态）。
 *
 * <p>{@code snapshot} 只在 {@link HubHallState#hasData()} 时非空；
 * 失败时保留一句人话 {@code note}，直接显示在总控表格的"说明"列里 ——
 * 值班的人第一眼要看到"为什么没数"，而不是去翻日志。</p>
 *
 * @param nodeKey 城内节点 key
 * @param name 殿名
 * @param state 连通状态
 * @param note 说明（刷新时刻 / 降级原因 / 无凭据）
 * @param snapshot 下游快照，可空
 * @author BlueWhale
 */
public record HubProbe(String nodeKey, String name, HubHallState state, String note, OverviewSnapshot snapshot) {

    /**
     * 主指标：下游第一条 KPI，按契约口径排好版（「营收 ¥123.4万」）
     *
     * <p>「营收 ¥123.4万」这种混排文本只在总控的行里出现：一列要同时装得下金额、
     * 计数与百分比，只能由服务端先排成字符串。排版口径来自
     * {@link Overviews#formatKpi}，与前端 {@code formatValue} 同一套规则。</p>
     *
     * @return 可显示文本；无数据时为 {@code —}
     */
    public String headline() {
        OverviewKpi kpi = firstKpi();
        if (kpi == null) {
            return "—";
        }
        return kpi.label() + " " + Overviews.formatKpi(kpi);
    }

    /**
     * 首条 KPI
     *
     * @return 指标，可空
     */
    public OverviewKpi firstKpi() {
        if (snapshot == null || snapshot.kpis() == null || snapshot.kpis().isEmpty()) {
            return null;
        }
        return snapshot.kpis().get(0);
    }
}
