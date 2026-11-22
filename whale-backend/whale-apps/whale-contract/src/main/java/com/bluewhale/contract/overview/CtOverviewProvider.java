package com.bluewhale.contract.overview;

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
 * 盟府（CONTRACT）概览：内城十二坊的第十一个坊——合同印章。
 *
 * <p><b>这一屏讲什么。</b>盟府掌契约：城里有多少份合同在履约、多少份
 * 卡在审批或签署的路上、多少份快到期没人续、多少份已经过期却还挂着
 * "生效中"。它不重复任何一殿：将作看器物的图册，营造看工程的推进，
 * 盟府看的是<b>纸面上的承诺</b>——合同金额、状态、到期这三本账。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link CtOverviewStatsService}：
 * "30 天内到期"与 2D 台账的 expiringSoon 筛选同窗口，
 * 状态聚合与 {@code ContractStatus} 枚举同源。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与
 * "一份合同都没签"长得一模一样。SDK 会把异常降级成 stale/degraded，
 * 屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CtOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "contract";

    /** 城内节点 key（盟府），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "mengfu-hall";

    /** 权限码：复用 2D 合同台账同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:contract:list";

    /** 合同台账列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String CONTRACT_PAGE_PATH = "/contract/page";

    /** 盟府统计口径 */
    private final CtOverviewStatsService statsService;

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
        return "盟府";
    }

    @Override
    public String subtitle() {
        return "合同印章";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        CtOverviewStats stats = statsService.snapshot();
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 契约台账")
                .note("口径：在途 = 审批中 + 已批待签 + 签署中 + 已签待生效；"
                        + "本月到期与 2D 台账 expiringSoon 同为 30 天窗口；"
                        + "已过期仍生效 = 到期日早于今天但状态仍是 EFFECTIVE；"
                        + "金额图只合计 CNY");
        appendKpis(builder, stats);
        appendMoneyChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：生效中、在途、本月到期、已过期仍生效。
     *
     * <p>"已过期仍生效"是这一屏唯一带重语气的牌：到期日本身只是提醒
     * （续签窗口），到期了还没人动才是盟府要介入的事。其余三张牌只报数。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, CtOverviewStats stats) {
        builder.kpi(Overviews.count("effective", "生效中合同", stats.effective(), OverviewTone.FLAT,
                        effectiveHint(stats)))
                .kpi(Overviews.count("in-flight", "在途合同", stats.inFlight(), OverviewTone.FLAT,
                        inFlightHint(stats)))
                .kpi(Overviews.count("expiring", "本月到期", stats.expiringSoon(),
                        expiringTone(stats), expiringHint(stats)))
                .kpi(Overviews.count("expired-active", "已过期仍生效", stats.expiredActive(),
                        expiredTone(stats), expiredHint(stats)));
    }

    /**
     * 生效牌的提示语：归档与终止是沉淀在库里的历史账，值得说一句。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String effectiveHint(CtOverviewStats stats) {
        if (stats.total() <= 0) {
            return "还没有登记合同";
        }
        return "已归档 " + stats.archived() + " · 已终止 " + stats.terminated();
    }

    /**
     * 在途牌的提示语：拆出审批与签署两段——卡在哪一段，要催的人不同。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String inFlightHint(CtOverviewStats stats) {
        if (stats.inFlight() <= 0) {
            return stats.draft() > 0 ? "有 " + stats.draft() + " 份起草未提交" : "没有在途合同";
        }
        return "审批中 " + stats.approving() + " · 签署中 " + stats.signing();
    }

    /**
     * 到期牌的语气：有合同快到期就黄——续签要提前谈，拖到过期就被动。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone expiringTone(CtOverviewStats stats) {
        return stats.expiringSoon() > 0 ? OverviewTone.WARN : OverviewTone.FLAT;
    }

    /**
     * 到期牌的提示语：驳回撤回是"这版没成得重来"的账，放在一起交代。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String expiringHint(CtOverviewStats stats) {
        if (stats.expiringSoon() <= 0) {
            return "30 天内没有到期合同";
        }
        return "30 天续签窗口，提前谈";
    }

    /**
     * 过期牌的语气：到期了还挂着生效就是红——要么续签要么终止，不能悬着。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone expiredTone(CtOverviewStats stats) {
        return stats.expiredActive() > 0 ? OverviewTone.BAD : OverviewTone.GOOD;
    }

    /**
     * 过期牌的提示语：没过期就明说，别让读者猜"0 是好是坏"。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String expiredHint(CtOverviewStats stats) {
        if (stats.expiredActive() <= 0) {
            return "没有悬着的过期合同";
        }
        return "要么续签要么终止，不能悬着";
    }

    /**
     * 生效中合同金额分布柱图：采购 / 销售 / 服务 / 租赁 / 其他。
     *
     * <p><b>为什么是金额不是份数。</b>盟府的账是钱账：同样 5 份合同，
     * 采购 4 份金额 8 成与销售 4 份金额 8 成是完全两种生意结构，
     * 份数看不出来。只合计生效中合同——在途的还没成为承诺，
     * 终止的已经不是承诺；外币不计入，数量在告警里交代。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendMoneyChart(Overviews.Builder builder, CtOverviewStats stats) {
        List<CtOverviewStats.CategoryAmount> amounts = stats.effectiveMoney();
        if (amounts == null || amounts.isEmpty()) {
            return;
        }
        List<String> categories = amounts.stream().map(CtOverviewStats.CategoryAmount::label).toList();
        List<Double> values = amounts.stream().map(CtOverviewStats.CategoryAmount::amount).toList();
        builder.chart(Overviews.chart("effective-money", "生效中合同金额分布", "bar",
                OverviewFormat.MONEY, categories, new OverviewSeries("金额（CNY）", values)));
    }

    /**
     * 入口：合同台账。
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("contracts", "合同台账", CONTRACT_PAGE_PATH, PERMISSION));
    }

    /**
     * 告警条：过期仍生效、即将到期、外币、起草未提交、驳回撤回、无合同。
     *
     * <p>这些事"数字本身不会喊"：{@code 3} 份过期不会自己变红，
     * 外币合同被金额图排除后如果不提示，读者会以为账丢了。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, CtOverviewStats stats) {
        if (stats.expiredActive() > 0) {
            builder.alert(OverviewAlertLevel.CRITICAL, "有 " + stats.expiredActive()
                    + " 份合同已过期仍挂着生效，续签或终止得有人拍板", alertCount(stats.expiredActive()));
        }
        if (stats.expiringSoon() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.expiringSoon()
                    + " 份合同 30 天内到期，续签要提前谈", alertCount(stats.expiringSoon()));
        }
        if (stats.foreignCurrency() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.foreignCurrency()
                    + " 份外币生效合同未计入金额图（不同币种不相加）", alertCount(stats.foreignCurrency()));
        }
        if (stats.draft() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.draft()
                    + " 份合同起草后一直没提交，纸面上还什么都没有", alertCount(stats.draft()));
        }
        if (stats.rejectWithdrawn() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.rejectWithdrawn()
                    + " 份合同被驳回或撤回，这版没成得重来", alertCount(stats.rejectWithdrawn()));
        }
        if (stats.total() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有登记合同，盟府暂时无账可理", null);
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
