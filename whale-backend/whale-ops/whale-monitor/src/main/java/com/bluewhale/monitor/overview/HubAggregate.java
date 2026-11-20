package com.bluewhale.monitor.overview;

import com.bluewhale.monitor.vo.HubHallDetailVO;
import com.bluewhale.monitor.vo.HubHallRowVO;
import com.bluewhale.monitor.vo.HubLineVO;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.support.Overviews;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次聚合的完整结果：给厅/牌看的快照 + 给屏风页看的逐殿明细。
 *
 * <p>两者必须出自同一次聚合：{@code snapshot} 是 KPI/图表/告警，{@code probes} 是
 * 每座殿的原始问询结果；如果分别取，厅里写「未通 1」而表格里全是通畅，
 * 这种自相矛盾会先摧毁"总控"这个页面的可信度。</p>
 *
 * @param snapshot 汇总结论（数枢殿这块牌子/这间厅画的东西）
 * @param probes 逐殿问询结果
 * @author BlueWhale
 */
public record HubAggregate(OverviewSnapshot snapshot, List<HubProbe> probes) {

    /** 详情页里最多引用几条下游告警（再多要看下游自己的页面） */
    private static final int DETAIL_ALERT_LIMIT = 3;

    /** 数据时刻的显示口径 */
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 表格行（每殿一行）
     *
     * @return 行列表，顺序与配置一致
     */
    public List<HubHallRowVO> rows() {
        List<HubHallRowVO> rows = new ArrayList<>(probes.size());
        for (HubProbe probe : probes) {
            rows.add(new HubHallRowVO(probe.nodeKey(), probe.nodeKey(), probe.name(), probe.state().value(),
                    probe.headline(), probe.note(), updatedLabelOf(probe.snapshot())));
        }
        return List.copyOf(rows);
    }

    /**
     * 一座殿的详情
     *
     * @param nodeKey 节点 key
     * @return 详情；节点不在册时返回 {@code null}
     */
    public HubHallDetailVO detail(String nodeKey) {
        HubProbe probe = probes.stream()
                .filter(item -> item.nodeKey().equals(nodeKey))
                .findFirst()
                .orElse(null);
        if (probe == null) {
            return null;
        }
        return new HubHallDetailVO(probe.nodeKey(), probe.name(), probe.state().value(),
                probe.snapshot() == null ? null : probe.snapshot().subtitle(),
                updatedLabelOf(probe.snapshot()),
                noteOf(probe),
                linesOf(probe.snapshot()));
    }

    /**
     * 详情页说明：把降级原因与下游告警拼成一句
     *
     * @param probe 问询结果
     * @return 说明文本
     */
    private String noteOf(HubProbe probe) {
        if (probe.snapshot() == null) {
            return probe.note();
        }
        List<String> alerts = probe.snapshot().alerts() == null
                ? List.of()
                : probe.snapshot().alerts().stream()
                        .limit(DETAIL_ALERT_LIMIT)
                        .map(alert -> alert.count() == null || alert.count() <= 1
                                ? alert.text()
                                : alert.text() + " ×" + alert.count())
                        .toList();
        return alerts.isEmpty() ? probe.note() : probe.note() + " · " + String.join("；", alerts);
    }

    /**
     * 详情页指标行
     *
     * @param snapshot 下游快照，可空
     * @return 指标行；无数据时为空列表
     */
    private List<HubLineVO> linesOf(OverviewSnapshot snapshot) {
        if (snapshot == null || snapshot.kpis() == null) {
            return List.of();
        }
        List<HubLineVO> lines = new ArrayList<>(snapshot.kpis().size());
        int no = 1;
        for (OverviewKpi kpi : snapshot.kpis()) {
            lines.add(new HubLineVO(no++, kpi.label(), Overviews.formatKpi(kpi)));
        }
        return List.copyOf(lines);
    }

    /**
     * 数据时刻的可显示文本（{@code MM-dd HH:mm}）
     *
     * <p>ISO-8601 直接放进 3D 屏风里长到没人读，还占掉一整列；这里统一压成
     * 值班最关心的那一段。解析失败时原样返回 —— 时间字段坏了不该让整行消失。</p>
     *
     * @param snapshot 下游快照，可空
     * @return 可显示文本；无数据时为空串
     */
    private String updatedLabelOf(OverviewSnapshot snapshot) {
        if (snapshot == null || snapshot.generatedAt() == null || snapshot.generatedAt().isBlank()) {
            return "";
        }
        try {
            return OffsetDateTime.parse(snapshot.generatedAt()).format(LABEL_FORMAT);
        } catch (DateTimeParseException e) {
            return snapshot.generatedAt();
        }
    }
}
