package com.bluewhale.pm.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSeries;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 营造（PM）概览：内城十二坊的第十个坊——工程调度。
 *
 * <p><b>这一屏讲什么。</b>营造司掌工程调度：城里有多少项工程在同时开工、
 * 每项工程按什么节奏推进、有多少活派了没人干、有多少活拖过了期限。
 * 它不重复任何一殿：工部看产线上的物，将作看器物的图册，
 * 营造看的是<b>人怎么把活推进</b>——项目、迭代、工作项这三本账，
 * 在 2D 后台要切看板 / 报表两个页面才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link PmOverviewStatsService}：
 * "未完成"的判定跟着状态分类（TODO / IN_PROGRESS / DONE）走，
 * 与 2D 看板按分类分列同一套口径——PM 的状态是每个项目自定义的，
 * 唯一跨项目稳定的就是分类。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与
 * "一个项目都没建"长得一模一样。SDK 会把异常降级成 stale/degraded，
 * 屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PmOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "pm";

    /** 城内节点 key（营造坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "yingzao-bureau";

    /** 权限码：复用 2D 项目台账同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:pm:project:list";

    /** 项目台账列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String PROJECT_PAGE_PATH = "/pm/project/page";

    /** 营造统计口径 */
    private final PmOverviewStatsService statsService;

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
        return "营造";
    }

    @Override
    public String subtitle() {
        return "工程调度";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        PmOverviewStats stats = statsService.snapshot();
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 三本账")
                .note("口径：在建项目 = status ACTIVE；活跃迭代 = ACTIVE；未完成 = 状态分类非 DONE"
                        + "（各项目自定义状态按分类归并）；逾期 = 截止日早于今天且未完成");
        appendKpis(builder, stats);
        appendTypeChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：在建项目、活跃迭代、未完成工作项、逾期未完成。
     *
     * <p>"逾期"是这一屏唯一带语气的牌：活没做完是常态（所以"未完成"只报数），
     * 拖过期限是调度要介入的事。其余三张牌只报数，语气留给告警条。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, PmOverviewStats stats) {
        builder.kpi(Overviews.count("projects", "在建项目", stats.projectActive(), OverviewTone.FLAT,
                        projectHint(stats)))
                .kpi(Overviews.count("sprints", "活跃迭代", stats.sprintActive(),
                        sprintTone(stats), sprintHint(stats)))
                .kpi(Overviews.count("items-open", "未完成工作项", stats.itemTodo() + stats.itemInProgress(),
                        OverviewTone.FLAT, itemHint(stats)))
                .kpi(Overviews.count("items-overdue", "逾期未完成", stats.itemOverdue(),
                        overdueTone(stats), overdueHint(stats)));
    }

    /**
     * 项目牌的提示语：归档项目是沉淀在库里的历史账，值得说一句。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String projectHint(PmOverviewStats stats) {
        if (stats.projectActive() <= 0 && stats.projectArchived() <= 0) {
            return "还没有建项目";
        }
        return "归档 " + stats.projectArchived() + " 个";
    }

    /**
     * 迭代牌的语气：有在建项目却没有任何进行中的迭代，等于工程挂了空档——黄。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone sprintTone(PmOverviewStats stats) {
        return stats.projectActive() > 0 && stats.sprintActive() <= 0
                ? OverviewTone.WARN : OverviewTone.FLAT;
    }

    /**
     * 迭代牌的提示语：写出迭代的结构，避免"活跃 2 个"看不出节奏。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String sprintHint(PmOverviewStats stats) {
        if (stats.sprintActive() + stats.sprintFuture() + stats.sprintClosed() <= 0) {
            return "还没有排迭代";
        }
        return "未启动 " + stats.sprintFuture() + " · 已关闭 " + stats.sprintClosed();
    }

    /**
     * 未完成牌的提示语：拆出"待办"与"进行中"——前者还没人接手，
     * 后者有人在做，两种积压的处理方式不同。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String itemHint(PmOverviewStats stats) {
        if (stats.itemTotal() <= 0) {
            return "还没有登记工作项";
        }
        return "待办 " + stats.itemTodo() + " · 进行中 " + stats.itemInProgress();
    }

    /**
     * 逾期牌的语气：有活拖过期限就红——逾期是调度要介入的头等事。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone overdueTone(PmOverviewStats stats) {
        return stats.itemOverdue() > 0 ? OverviewTone.BAD : OverviewTone.GOOD;
    }

    /**
     * 逾期牌的提示语：拆出高优先级——逾期的高优活要先救。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String overdueHint(PmOverviewStats stats) {
        if (stats.itemOverdue() <= 0) {
            return "没有拖过期限的活";
        }
        return "高优先级 " + stats.itemOverdueHigh();
    }

    /**
     * 未完成工作项类型分布柱图：史诗 / 需求 / 任务 / 缺陷 / 子任务。
     *
     * <p><b>为什么是分布不是趋势。</b>营造的账是存量不是流量：工作项今天比昨天
     * 多几件没有多少信息量，"未完成的活里缺陷占了多少"这种结构才是
     * 调度要先看的东西。类型码值固定五种，柱图比折线少一根轴的歧义。
     * 只数未完成：完成项的类型分布是历史账。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendTypeChart(Overviews.Builder builder, PmOverviewStats stats) {
        List<PmOverviewStats.TypeCount> counts = stats.itemByType();
        if (counts == null || counts.isEmpty()) {
            return;
        }
        List<String> categories = counts.stream().map(PmOverviewStats.TypeCount::label).toList();
        List<Double> values = counts.stream().map(item -> (double) item.count()).toList();
        builder.chart(Overviews.chart("item-types", "未完成工作项类型分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("未完成件数", values)));
    }

    /**
     * 入口：项目台账。
     *
     * <p>只挂项目一个入口：工作项在 2D 里才有流转、评论、打分等操作按钮，
     * 城里点进去也只能看；项目台账是纯只读的清单，正适合摆在屏风上。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("projects", "项目台账", PROJECT_PAGE_PATH, PERMISSION));
    }

    /**
     * 告警条：逾期、无主、空档迭代、归档沉淀、无项目。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 件逾期不会自己变红，
     * {@code 0} 个活跃迭代与"还没排迭代"看起来一样。
     * "有在建项目但没有任何进行中的迭代"单独成条——工程在册却没人排期，
     * 停工不是排到下个周期那么简单。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, PmOverviewStats stats) {
        if (stats.itemOverdue() > 0) {
            builder.alert(OverviewAlertLevel.CRITICAL, "有 " + stats.itemOverdue()
                    + " 件工作项拖过期限仍未完成，调度要介入", alertCount(stats.itemOverdue()));
        }
        if (stats.itemUnassigned() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.itemUnassigned()
                    + " 件未完成的活没派负责人，没人接手就一直挂着", alertCount(stats.itemUnassigned()));
        }
        if (stats.projectActive() > 0 && stats.sprintActive() <= 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有在建项目但没有任何进行中的迭代，工程挂了空档", null);
        }
        if (stats.sprintFuture() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.sprintFuture()
                    + " 个迭代还没启动，启动前工作项只排不办", null);
        }
        if (stats.projectArchived() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.projectArchived()
                    + " 个项目已归档，其工作项不再更新", null);
        }
        if (stats.projectActive() <= 0 && stats.projectArchived() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有建项目，营造暂时无账可理", null);
        }
    }

    /**
     * 告警计数：契约只收 {@code Integer}，超长时截顶而不是抛异常。
     *
     * @param value 计数
     * @return 截顶后的整数
     */
    private static Integer alertCount(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
