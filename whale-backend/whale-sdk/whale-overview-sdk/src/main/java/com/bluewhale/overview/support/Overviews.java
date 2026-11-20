package com.bluewhale.overview.support;

import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSeries;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.model.OverviewTone;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 快照组装器与常用工厂：让 provider 只写"取什么数"，不写"怎么摆"。
 *
 * <p>21 处接入若各自 new 一遍 record，字段顺序、缺省语气、单位口径必然漂移；
 * 这里把最常用的三种指标（计数/金额/百分比）与三种图形做成工厂，
 * provider 侧一行一个指标，读起来就是业务本身。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * return Overviews.builder("erp", "hubu-hall", "户部")
 *         .subtitle("经营驾驶舱 · 本月")
 *         .kpi(Overviews.money("revenue", "营收", cockpit.getRevenueAmount()))
 *         .kpi(Overviews.count("orders", "订单", cockpit.getOrderCount()))
 *         .kpi(Overviews.count("alerts", "预警", cockpit.getAlertCount(), OverviewTone.WARN, "未关闭"))
 *         .alert(OverviewAlertLevel.WARNING, "3 个订单超期未发货", 3)
 *         .build();
 * }</pre>
 *
 * @author BlueWhale
 */
public final class Overviews {

    /** 生成时刻口径：ISO-8601 带时区 */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 私有构造器：工具类
     */
    private Overviews() {
    }

    /**
     * 开始组装一个快照
     *
     * @param service 服务一级前缀
     * @param nodeKey 城内节点 key
     * @param title 标题
     * @return 组装器
     */
    public static Builder builder(String service, String nodeKey, String title) {
        return new Builder(service, nodeKey, title);
    }

    /**
     * 计数指标
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param value 数值
     * @return 指标
     */
    public static OverviewKpi count(String key, String label, Number value) {
        return count(key, label, value, OverviewTone.FLAT, null);
    }

    /**
     * 计数指标（带语气与口径说明）
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param value 数值
     * @param tone 语气
     * @param hint 口径说明
     * @return 指标
     */
    public static OverviewKpi count(String key, String label, Number value, OverviewTone tone, String hint) {
        return kpi(key, label, value, OverviewFormat.COUNT, tone, hint);
    }

    /**
     * 金额指标
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param value 金额
     * @return 指标
     */
    public static OverviewKpi money(String key, String label, Number value) {
        return money(key, label, value, OverviewTone.FLAT, null);
    }

    /**
     * 金额指标（带语气与口径说明）
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param value 金额
     * @param tone 语气
     * @param hint 口径说明
     * @return 指标
     */
    public static OverviewKpi money(String key, String label, Number value, OverviewTone tone, String hint) {
        return kpi(key, label, value, OverviewFormat.MONEY, tone, hint);
    }

    /**
     * 百分比指标（值传比率）
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param ratio 比率，0.82 表示 82%
     * @return 指标
     */
    public static OverviewKpi percent(String key, String label, Number ratio) {
        return percent(key, label, ratio, OverviewTone.FLAT, null);
    }

    /**
     * 百分比指标（带语气与口径说明）
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param ratio 比率，0.82 表示 82%
     * @param tone 语气
     * @param hint 口径说明
     * @return 指标
     */
    public static OverviewKpi percent(String key, String label, Number ratio, OverviewTone tone, String hint) {
        return kpi(key, label, ratio, OverviewFormat.PERCENT, tone, hint);
    }

    /**
     * 通用指标构造
     *
     * @param key 唯一键
     * @param label 牌面文字
     * @param value 数值，{@code null} 记为 0（宁可显示 0 也不让整块缺失）
     * @param format 排版口径
     * @param tone 语气
     * @param hint 口径说明
     * @return 指标
     */
    public static OverviewKpi kpi(String key, String label, Number value, OverviewFormat format,
                                  OverviewTone tone, String hint) {
        double safe = value == null ? 0d : value.doubleValue();
        return new OverviewKpi(key, label, safe, format, null, tone, null, hint);
    }

    /**
     * 补全指标的可选字段（单位、环比、语气、口径说明）
     *
     * <p>大多数指标用 {@link #count}/{@link #money}/{@link #percent} 的简写就够；
     * 需要单位（"件""秒"）或环比时才走这个方法，避免为每种组合都加一个重载。</p>
     *
     * @param kpi 原指标
     * @param tone 语气，可空
     * @param unit 单位，可空
     * @param hint 口径说明，可空
     * @param deltaPercent 环比比率，可空
     * @return 新指标
     */
    public static OverviewKpi decorate(OverviewKpi kpi, OverviewTone tone, String unit, String hint, Double deltaPercent) {
        if (kpi == null) {
            return null;
        }
        return new OverviewKpi(kpi.key(), kpi.label(), kpi.value(), kpi.format(),
                unit == null ? kpi.unit() : unit,
                tone == null ? kpi.tone() : tone,
                deltaPercent == null ? kpi.deltaPercent() : deltaPercent,
                hint == null ? kpi.hint() : hint);
    }

    /**
     * 一条序列
     *
     * @param name 序列名
     * @param values 数值
     * @return 序列
     */
    public static OverviewSeries series(String name, Number... values) {
        List<Double> list = new ArrayList<>();
        if (values != null) {
            Arrays.stream(values).forEach(value -> list.add(value == null ? 0d : value.doubleValue()));
        }
        return new OverviewSeries(name, List.copyOf(list));
    }

    /**
     * 一张图表
     *
     * @param key 唯一键
     * @param title 图题
     * @param type line / bar / donut
     * @param format 刻度口径
     * @param categories 横轴标签
     * @param series 序列
     * @return 图表
     */
    public static OverviewChart chart(String key, String title, String type, OverviewFormat format,
                                      List<String> categories, OverviewSeries... series) {
        return new OverviewChart(key, title, type, format, categories,
                series == null ? List.of() : List.of(series));
    }

    /**
     * 一条跳转入口
     *
     * @param key 唯一键
     * @param label 入口文字
     * @param path 屏风页路径
     * @param permission 额外权限码，可空
     * @return 入口
     */
    public static OverviewLink link(String key, String label, String path, String permission) {
        return new OverviewLink(key, label, path, permission);
    }

    /**
     * 快照组装器：链式累积，{@link #build()} 时统一补元字段
     *
     * <p>不提供 {@code state} 设置：状态由 SDK 依据实际装载结果盖章，
     * provider 说自己 live 不算数（否则降级就永远标不出来）。</p>
     *
     * @author BlueWhale
     */
    public static final class Builder {

        /** 服务前缀 */
        private final String service;

        /** 节点 key */
        private final String nodeKey;

        /** 标题 */
        private final String title;

        /** 副标题 */
        private String subtitle;

        /** 指标 */
        private final List<OverviewKpi> kpis = new ArrayList<>();

        /** 图表 */
        private final List<OverviewChart> charts = new ArrayList<>();

        /** 告警 */
        private final List<OverviewAlert> alerts = new ArrayList<>();

        /** 入口 */
        private final List<OverviewLink> links = new ArrayList<>();

        /** 说明 */
        private final List<String> notes = new ArrayList<>();

        /**
         * 构造组装器
         *
         * @param service 服务前缀
         * @param nodeKey 节点 key
         * @param title 标题
         */
        private Builder(String service, String nodeKey, String title) {
            this.service = service;
            this.nodeKey = nodeKey;
            this.title = title;
        }

        /**
         * 设置副标题（口径与统计期间）
         *
         * @param value 副标题
         * @return 自身
         */
        public Builder subtitle(String value) {
            this.subtitle = value;
            return this;
        }

        /**
         * 追加指标
         *
         * @param kpi 指标
         * @return 自身
         */
        public Builder kpi(OverviewKpi kpi) {
            if (kpi != null) {
                this.kpis.add(kpi);
            }
            return this;
        }

        /**
         * 追加指标
         *
         * @param items 指标
         * @return 自身
         */
        public Builder kpis(List<OverviewKpi> items) {
            if (items != null) {
                this.kpis.addAll(items);
            }
            return this;
        }

        /**
         * 追加图表
         *
         * @param chart 图表
         * @return 自身
         */
        public Builder chart(OverviewChart chart) {
            if (chart != null) {
                this.charts.add(chart);
            }
            return this;
        }

        /**
         * 追加告警
         *
         * @param alert 告警
         * @return 自身
         */
        public Builder alert(OverviewAlert alert) {
            if (alert != null) {
                this.alerts.add(alert);
            }
            return this;
        }

        /**
         * 追加告警（简写）
         *
         * @param level 严重度
         * @param text 说明
         * @param count 条数，可空
         * @return 自身
         */
        public Builder alert(OverviewAlertLevel level, String text, Integer count) {
            return alert(new OverviewAlert(level, text, count, null));
        }

        /**
         * 追加入口
         *
         * @param link 入口
         * @return 自身
         */
        public Builder link(OverviewLink link) {
            if (link != null) {
                this.links.add(link);
            }
            return this;
        }

        /**
         * 追加说明（降级原因、被裁剪的块）
         *
         * @param note 说明
         * @return 自身
         */
        public Builder note(String note) {
            if (note != null && !note.isBlank()) {
                this.notes.add(note);
            }
            return this;
        }

        /**
         * 生成快照
         *
         * @return 状态为 {@code live} 的快照（最终状态由 SDK 盖章）
         */
        public OverviewSnapshot build() {
            return new OverviewSnapshot(service, nodeKey, title, subtitle, OffsetDateTime.now().format(ISO),
                    OverviewState.LIVE, List.copyOf(kpis), List.copyOf(charts), List.copyOf(alerts),
                    List.copyOf(links), List.copyOf(notes)).normalized();
        }
    }
}
