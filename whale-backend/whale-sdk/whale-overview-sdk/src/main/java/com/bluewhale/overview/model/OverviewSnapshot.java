package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 服务概览快照 —— 数据厅 / 牌坊 / 屏风都只认这一个形状。
 *
 * <p>形状由 {@code docs/specs/overview-contract} 定义，前端 TS 类型从同一份 JSON Schema 生成。
 * 这里的 {@link #normalized()} 负责把"provider 少填了什么"补齐成可安全绘制的快照：
 * 空列表安全化、图表长度自检、告警按严重度排序。**降级也走同一个形状**，
 * 前端不需要为"接口挂了"写第二套解析分支。</p>
 *
 * @param service 服务一级前缀，与网关路由一致
 * @param nodeKey 城内节点 key
 * @param title 厅/牌主标题
 * @param subtitle 口径与统计期间
 * @param generatedAt 生成时刻，ISO-8601 带时区
 * @param state 三态
 * @param kpis 指标
 * @param charts 图表
 * @param alerts 告警
 * @param links 跳转入口
 * @param notes 人可读的状态说明（降级原因、被裁剪的块）
 * @author BlueWhale
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewSnapshot(
        String service,
        String nodeKey,
        String title,
        String subtitle,
        String generatedAt,
        OverviewState state,
        List<OverviewKpi> kpis,
        List<OverviewChart> charts,
        List<OverviewAlert> alerts,
        List<OverviewLink> links,
        List<String> notes) {

    /** 告警排序：严重度降序，同级保持 provider 给的顺序 */
    private static final Comparator<OverviewAlert> ALERT_ORDER =
            Comparator.comparingInt(a -> -a.level().ordinal());

    /**
     * 规范化：空列表安全化、逐块补缺省值、图表错位剔除、告警排序
     *
     * @return 可安全渲染的快照
     */
    public OverviewSnapshot normalized() {
        List<OverviewChart> safeCharts = new ArrayList<>();
        for (OverviewChart chart : charts == null ? List.<OverviewChart>of() : charts) {
            OverviewChart normalized = chart.normalized();
            if (normalized.isAligned()) {
                safeCharts.add(normalized);
            }
        }
        List<OverviewAlert> safeAlerts = new ArrayList<>();
        for (OverviewAlert alert : alerts == null ? List.<OverviewAlert>of() : alerts) {
            safeAlerts.add(alert.normalized());
        }
        safeAlerts.sort(ALERT_ORDER);
        return new OverviewSnapshot(
                service,
                nodeKey,
                title,
                subtitle,
                generatedAt,
                state == null ? OverviewState.DEGRADED : state,
                kpis == null ? List.of() : kpis.stream().map(OverviewKpi::normalized).toList(),
                List.copyOf(safeCharts),
                List.copyOf(safeAlerts),
                links == null ? List.of() : List.copyOf(links),
                notes == null ? List.of() : List.copyOf(notes));
    }

    /**
     * 派生出"沿用上次成功"的快照：状态改 stale 并追加说明
     *
     * <p>只改状态与说明，不动数据 —— 陈旧的数据仍然比空着有价值，
     * 但必须自己说破，否则牌上写着昨天的数字而无人知晓。</p>
     *
     * @param note 降级原因，写入 notes 末尾
     * @return 新快照
     */
    public OverviewSnapshot asStale(String note) {
        return withState(OverviewState.STALE, note);
    }

    /**
     * 派生出"不可用"快照：状态改 degraded 并追加说明
     *
     * @param note 降级原因，写入 notes 末尾
     * @return 新快照
     */
    public OverviewSnapshot asDegraded(String note) {
        return withState(OverviewState.DEGRADED, note);
    }

    /**
     * 替换状态并追加一条说明
     *
     * @param next 新状态
     * @param note 说明，空白则不加
     * @return 新快照
     */
    private OverviewSnapshot withState(OverviewState next, String note) {
        List<String> merged = new ArrayList<>(notes == null ? List.of() : notes);
        if (note != null && !note.isBlank()) {
            merged.add(note);
        }
        return new OverviewSnapshot(service, nodeKey, title, subtitle, generatedAt, next,
                kpis, charts, alerts, links, List.copyOf(merged));
    }

    /**
     * 构造一个从未成功过的空快照（provider 不可用时由 SDK 兜底）
     *
     * @param service 服务前缀
     * @param nodeKey 节点 key
     * @param title 标题
     * @param subtitle 副标题
     * @param note 降级原因
     * @param generatedAt 生成时刻
     * @return 状态为 degraded 的空快照
     */
    public static OverviewSnapshot empty(String service, String nodeKey, String title, String subtitle,
                                         String note, String generatedAt) {
        return new OverviewSnapshot(service, nodeKey, title, subtitle, generatedAt, OverviewState.DEGRADED,
                List.of(), List.of(), List.of(), List.of(), note == null ? List.of() : List.of(note));
    }
}
