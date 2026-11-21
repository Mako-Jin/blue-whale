package com.bluewhale.file.overview;

import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.BucketCount;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.DayCount;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.LibraryStat;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.RecycleStat;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 藏书殿（文件与存储）概览：城内"东西放在哪儿、占了多少"这一路的第一个真节点（T5.4）。
 *
 * <p><b>为什么指标选这四个。</b>藏书殿值班要回答的是：一共多少东西（文件总数）、
 * 盘子还够不够（占用空间）、今天有没有人往里搬（今日新增）、删掉的东西清干净没有（回收站）。
 * 前两个是容量，后两个是流动——只看容量会在一天之内被上传打满，只看流动会漏掉"盘快满了但今天没人传"。</p>
 *
 * <p><b>「文件数」与「占用空间」口径不同，这是刻意的。</b>文件数按根文件计（与文件台账列表
 * 同口径，用户数得清），占用空间含全部版本行（版本行不在列表里，但它实实在在占盘）。
 * 两个数用同一口径的后果是一边虚高、一边骗人。</p>
 *
 * <p><b>孤儿对象对账不在这里做。</b>对账要列举存储桶，量级与耗时都不适合放进 5 秒的概览超时；
 * 概览只报"已过期未清理"这种一定能从库里算出来的信号。</p>
 *
 * <p><b>为什么不 catch 异常。</b>库挂了就让它抛——SDK 会降级成 {@code stale} / {@code degraded}。
 * provider 自己吞异常返回四个 0，会让"文件库挂了"长得跟"这个租户一份文件都没有"一模一样。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class FileOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /file/** 路由一致） */
    private static final String SERVICE = "file";

    /** 城内节点 key（藏书殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "cangshu-hall";

    /** 概览权限码：与 2D 文件列表同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:file:list";

    /** 屏风页（文件台账）路径：类前缀 {@code /file/v1} + 方法映射 {@code /page} */
    private static final String FILE_PAGE_PATH = "/file/v1/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 单字节到 GB 的除数 */
    private static final double BYTES_PER_GB = 1024d * 1024d * 1024d;

    /** 回收站占用告警阈值（GB）：超过这个量基本可以确认有人删了一批大的 */
    private static final double RECYCLE_ALERT_GB = 5d;

    /** 桶构成图最多显示的类目数；超出的合并成「其他」，不静默丢弃 */
    private static final int BUCKET_TOP = 5;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final FileLibraryStatQuery statQuery;

    /** 文件服务配置：回收站保留期与清理开关决定"超期未清理"是否算故障 */
    private final FileProperties fileProperties;

    @Override
    public String service() {
        return SERVICE;
    }

    @Override
    public String nodeKey() {
        return NODE_KEY;
    }

    @Override
    public String title() {
        return "藏书殿";
    }

    @Override
    public String subtitle() {
        return "文件与存储";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        Long tenantId = context == null ? null : context.tenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        LibraryStat library = statQuery.load(tenantId);
        long todayFiles = statQuery.countCreated(tenantId, today.atStartOfDay(), today.plusDays(1L).atStartOfDay());
        RecycleStat recycle = statQuery.recycle(tenantId, staleBefore(now));
        List<DayCount> trend = statQuery.countCreatedByDay(tenantId,
                today.minusDays(TREND_DAYS - 1L), today.plusDays(1L));
        Map<LocalDate, Long> byDay = pivot(trend);
        List<BucketCount> buckets = statQuery.byBucket(tenantId);

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.decorate(Overviews.count("files", "文件总数", library.files(),
                        OverviewTone.FLAT, "不含历史版本"), null, "份", null, null))
                .kpi(Overviews.decorate(Overviews.count("bytes", "占用空间", toGb(library.bytes()),
                        OverviewTone.FLAT, "含各版本行"), null, "GB", null, null))
                .kpi(Overviews.decorate(Overviews.count("today", "今日新增", todayFiles,
                        todayFiles > 0 ? OverviewTone.GOOD : OverviewTone.FLAT, "按根文件计"),
                        null, "份", null, null))
                .kpi(Overviews.decorate(Overviews.count("recycle", "回收站", recycle.files(),
                                recycle.staleFiles() > 0 ? OverviewTone.WARN : OverviewTone.FLAT,
                                recycle.staleFiles() > 0 ? "其中超期 " + recycle.staleFiles() + " 份" : "未过保留期"),
                        null, "份", null, null))
                .chart(Overviews.chart("upload-trend", "近 " + TREND_DAYS + " 日新增文件",
                        "line", OverviewFormat.COUNT, labels(today),
                        Overviews.series("新增", seriesOf(byDay, today))))
                .chart(bucketMix(buckets))
                .note("口径：文件数按根文件计（与文件台账同口径，不含历史版本），"
                        + "占用空间含全部版本行；回收站只统计已删除的根文件");
        if (tenantId == null) {
            /* 没租户上下文时四个数都会是 0——不写清楚，看的人会以为这个租户真的一份文件都没有。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("files", "文件台账", FILE_PAGE_PATH, PERMISSION));
        }
        appendAlert(builder, recycle, now);
        return builder.build();
    }

    /**
     * 超期判定线：删除时间早于它即为"该清未清"
     *
     * <p>保留期 {@code <= 0} 表示"无限期保留"，此时没有"超期"这回事，返回 {@code null}
     * 让端口返回 0——否则每一份被删的文件都会变成一条告警，值班的人第二天就把告警关了。</p>
     *
     * @param now 当前时刻
     * @return 判定线；保留期未启用时 {@code null}
     */
    private LocalDateTime staleBefore(LocalDateTime now) {
        long retentionDays = fileProperties.getRecycle().getRetentionDays();
        if (retentionDays <= 0L) {
            return null;
        }
        return now.minusDays(retentionDays);
    }

    /**
     * 桶构成图
     *
     * <p>超过 {@value #BUCKET_TOP} 个桶时，保留前 {@value #BUCKET_TOP} 个并把其余合并成
     * 「其他」——柱图里 12 个细柱谁都不看清，而"少掉的那几个桶"如果不出现，
     * 又会让各柱合计与文件总数对不上。</p>
     *
     * @param buckets 按桶统计
     * @return 图表
     */
    private OverviewChart bucketMix(List<BucketCount> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            /* 空库时给一根 0 值柱 + 一个说明类目：完全空的图会被渲染成"没数据"，
                 而"这个租户一份文件都没有"本身是有效信息。 */
            return Overviews.chart("bucket-mix", "按桶分布", "bar", OverviewFormat.COUNT,
                    List.of("暂无文件"), Overviews.series("文件数", 0L));
        }
        List<String> names = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        long rest = 0L;
        for (int i = 0; i < buckets.size(); i++) {
            BucketCount bucket = buckets.get(i);
            if (i < BUCKET_TOP) {
                names.add(bucket.bucket());
                values.add(bucket.files());
            } else {
                rest += bucket.files();
            }
        }
        if (buckets.size() > BUCKET_TOP) {
            names.add("其他");
            values.add(rest);
        }
        return Overviews.chart("bucket-mix", "按桶分布", "bar", OverviewFormat.COUNT,
                List.copyOf(names), Overviews.series("文件数", values.toArray(new Number[0])));
    }

    /**
     * 追加回收站告警
     *
     * <p>两条可以并存，因为根因不同：一条指向"清理作业没跑"（与作业心跳同源的静默失效），
     * 一条指向"该调保留期或该催清理"（容量问题）。若清理本身没开启，两条都不报，
     * 改用 note 说明——配置成不清理不是故障。</p>
     *
     * @param builder 组装器
     * @param recycle 回收站统计
     * @param now 当前时刻
     */
    private void appendAlert(Overviews.Builder builder, RecycleStat recycle, LocalDateTime now) {
        long retentionDays = fileProperties.getRecycle().getRetentionDays();
        boolean purgeOn = fileProperties.getRecycle().isPurgeEnabled() && retentionDays > 0L;
        if (!purgeOn) {
            builder.note("回收站到期清理未启用（whale.file.recycle.purge-enabled / retention-days），"
                    + "回收站内的文件不会被自动清理");
            return;
        }
        if (recycle.staleFiles() > 0L) {
            builder.alert(OverviewAlertLevel.WARNING,
                    "回收站有 " + recycle.staleFiles() + " 份文件已过 " + retentionDays
                            + " 天保留期未清理，清理作业可能没跑", (int) recycle.staleFiles());
        }
        double recycleGb = toGb(recycle.bytes());
        if (recycleGb >= RECYCLE_ALERT_GB) {
            builder.alert(OverviewAlertLevel.WARNING,
                    String.format(Locale.CHINA, "回收站占着 %.1f GB（%d 份），确认保留期或手动清理",
                            recycleGb, recycle.files()), (int) recycle.files());
        }
    }

    /**
     * 字节数转 GB（保留一位小数由渲染层决定，这里只做除法）
     *
     * @param bytes 字节数
     * @return GB 数
     */
    private double toGb(long bytes) {
        return bytes <= 0L ? 0d : bytes / BYTES_PER_GB;
    }

    /**
     * 把按天聚合归并成 日期 → 新增数
     *
     * @param rows 聚合行
     * @return 日期映射
     */
    private Map<LocalDate, Long> pivot(List<DayCount> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DayCount row : rows) {
            if (row == null || row.day() == null) {
                continue;
            }
            byDay.merge(row.day(), row.files(), Long::sum);
        }
        return byDay;
    }

    /**
     * 横轴标签：近 {@value #TREND_DAYS} 天，最后一个是今天
     *
     * @param today 今天
     * @return 标签列表
     */
    private List<String> labels(LocalDate today) {
        List<String> labels = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            labels.add(DAY_LABEL.format(today.minus(i, ChronoUnit.DAYS)));
        }
        return List.copyOf(labels);
    }

    /**
     * 新增文件数的 7 日序列（缺日补 0）
     *
     * <p>缺日必须补 0：跳过空日会让折线把"8 天前"接到"昨天"，图上看起来天天有人上传。</p>
     *
     * @param byDay 按天聚合
     * @param today 今天
     * @return 数值序列
     */
    private Number[] seriesOf(Map<LocalDate, Long> byDay, LocalDate today) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = today.minus(TREND_DAYS - 1L - i, ChronoUnit.DAYS);
            Long count = byDay.get(day);
            values[i] = count == null ? 0L : count;
        }
        return values;
    }
}
