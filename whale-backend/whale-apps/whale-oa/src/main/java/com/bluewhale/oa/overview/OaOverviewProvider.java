package com.bluewhale.oa.overview;

import com.bluewhale.oa.analytics.domain.model.ReportRows;
import com.bluewhale.oa.analytics.domain.repository.ReportRepository;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 吏部（OA）概览：内城十二坊的第二个坊——人事考课。
 *
 * <p><b>这一屏讲什么。</b>吏部掌"人"：谁在册、今天谁没打卡、今天谁迟到、本月考勤
 * 有多少人次异常。这不与政事殿重复——政事殿看的是**流程引擎**里哪条审批链断了，
 * 这里看的是**人有没有到岗**。城里两座殿报同一个数（都在讲"审批"）才是坏设计。</p>
 *
 * <p><b>口径不另造。</b>考勤数字一律走 {@link ReportRepository#attendanceMonthly} ——
 * 与 2D「数据报表 → 考勤月报」**同一条 SQL**（{@code ReportQueryMapper#attendanceMonthly}）。
 * 若在这里另写一条 {@code count(*)}，迟到/缺卡的判定（{@code late_minutes > 0}、
 * {@code absent = 1}）就会出现第二份定义，迟早与报表页对不上，而没人能判断谁对。</p>
 *
 * <p><b>"在职人数"是另一个上下文的口径，所以要单独裁剪。</b>它取自人事档案
 * （{@code oa_employee_profile}），不是 Auth 的用户数，也不等于编制。牌面上写
 * "按人事档案"而不是"在职员工"，就是为了让这处差异显式可读。没有
 * {@code sys:oa:hr:profile:list} 的人连这块牌都不下发——契约 §2.5 要求 provider 裁，
 * 截到前端再隐藏等于把"人事有多少人"白送。</p>
 *
 * <p><b>不在这里 catch 异常。</b>数据库挂了就让它抛，由 SDK 降级成 {@code stale} /
 * {@code degraded}。自己吞掉再返回 0，会让"接口挂了"与"今天没人迟到"长得一模一样——
 * 这正是本项目最常见的静默失效形态。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class OaOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "oa";

    /** 城内节点 key（吏部坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "libu-hall";

    /** 权限码：复用 2D 考勤日报页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:oa:attendance:daily:list";

    /** 员工档案权限码：没有它就连"在职人数"这块牌都不下发 */
    private static final String PROFILE_PERMISSION = "sys:oa:hr:profile:list";

    /** 考勤日报列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String DAILY_PAGE_PATH = "/oa/v1/attendance/daily/page";

    /** 已离职状态码（{@code EmploymentStatus.LEFT}）：在职人数 = 档案数 − 离职数 */
    private static final String STATUS_LEFT = "LEFT";

    /** 报表聚合仓储（考勤月报口径的来源） */
    private final ReportRepository reportRepository;

    /** 员工档案仓储（在册人数） */
    private final EmployeeProfileRepository employeeProfileRepository;

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
        return "吏部";
    }

    @Override
    public String subtitle() {
        return "人事考课";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        /* 两次查询，口径同一个 SQL：本月区间算"本月异常人次"，今天区间算"今天谁没来"。
           月初那天两个区间完全重合，此时直接复用本月结果，不再扫第二遍同一天的数据。 */
        List<ReportRows.AttendanceRow> monthRows = reportRepository.attendanceMonthly(monthStart, today);
        List<ReportRows.AttendanceRow> todayRows = monthStart.equals(today)
                ? monthRows
                : reportRepository.attendanceMonthly(today, today);

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + monthStart + " ~ " + today)
                .note("口径：考勤日报 oa_att_daily，与 2D 考勤月报同一条 SQL");
        appendHeadcount(builder, context);
        appendAttendanceKpis(builder, todayRows, monthRows);
        appendAbnormalChart(builder, monthRows);
        appendLinks(builder);
        appendAlerts(builder, todayRows, monthRows);
        return builder.build();
    }

    /**
     * 在册人数：人事档案总数减已离职数。
     *
     * <p>不用 {@code count(employmentStatus = 'ACTIVE')}：在职还包含试用期
     * （{@code PROBATION}）与转正（{@code REGULAR}），只数 {@code ACTIVE} 会把大半
     * 在册人员漏掉，而牌面上那个数看起来仍然"像个正常数字"，不会有人发现。</p>
     *
     * @param builder 组装器
     * @param context 调用者身份与权限码
     */
    private void appendHeadcount(Overviews.Builder builder, OverviewContext context) {
        if (context == null || !context.hasPermission(PROFILE_PERMISSION)) {
            return;
        }
        long total = employeeProfileRepository.count(new EmployeeProfileQuery());
        EmployeeProfileQuery leftQuery = new EmployeeProfileQuery();
        leftQuery.setEmploymentStatus(STATUS_LEFT);
        long left = employeeProfileRepository.count(leftQuery);
        builder.kpi(Overviews.count("headcount", "在职人数", Math.max(total - left, 0L),
                OverviewTone.FLAT, "按人事档案"));
    }

    /**
     * 考勤三张牌：今日缺卡、今日迟到、本月出勤。
     *
     * <p>"缺卡"与"迟到"数的是**人数**（今天有几个人异常），"本月出勤"数的是**人次**
     * （每人每天算一次）。两者单位不同，所以提示语必须各自写清，不能让读牌的人
     * 把 120 人次当成 120 个人。</p>
     *
     * @param builder 组装器
     * @param todayRows 今日考勤日汇总（每人一行）
     * @param monthRows 本月考勤日汇总（每人一行，值为区间内累计）
     */
    private static void appendAttendanceKpis(Overviews.Builder builder,
            List<ReportRows.AttendanceRow> todayRows, List<ReportRows.AttendanceRow> monthRows) {
        long absentToday = todayRows.stream().filter(row -> value(row.getAbsentDays()) > 0).count();
        long lateToday = todayRows.stream().filter(row -> value(row.getLateDays()) > 0).count();
        long workDays = monthRows.stream().mapToLong(row -> value(row.getWorkDays())).sum();
        builder.kpi(Overviews.count("absent", "今日缺卡", absentToday,
                        absentToday > 0 ? OverviewTone.BAD : OverviewTone.GOOD,
                        todayRows.isEmpty() ? "今日无汇总" : "今日在册 " + todayRows.size() + " 人"))
                .kpi(Overviews.count("late", "今日迟到", lateToday,
                        lateToday > 0 ? OverviewTone.WARN : OverviewTone.GOOD, "迟到分钟 > 0 的人数"))
                .kpi(Overviews.count("work-days", "本月出勤", workDays, OverviewTone.FLAT,
                        "人次（每日汇总累计）"));
    }

    /**
     * 本月异常柱状图：迟到 / 早退 / 缺卡。
     *
     * <p>三项都是"人次"——同一套 {@code oa_att_daily} 聚合出来的天数之和。
     * 数值缺省补 0：图表里的 {@code null} 会画成 NaN 柱，整张卡静默变白。</p>
     *
     * @param builder 组装器
     * @param monthRows 本月考勤日汇总
     */
    private static void appendAbnormalChart(Overviews.Builder builder, List<ReportRows.AttendanceRow> monthRows) {
        long late = monthRows.stream().mapToLong(row -> value(row.getLateDays())).sum();
        long early = monthRows.stream().mapToLong(row -> value(row.getEarlyDays())).sum();
        long absent = monthRows.stream().mapToLong(row -> value(row.getAbsentDays())).sum();
        builder.chart(Overviews.chart("abnormal", "本月考勤异常（人次）", "bar", OverviewFormat.COUNT,
                List.of("迟到", "早退", "缺卡"),
                Overviews.series("人次", late, early, absent)));
    }

    /**
     * 入口：只挂考勤日报。
     *
     * <p>不挂"员工档案"：那份视图（{@code EmployeeProfileView}）只有 userId 与 deptId，
     * 姓名与部门名都在 Auth 侧，城里摆出来是一张全是数字的名录。入口指向一页读不懂的
     * 列表，比没有入口更糟——所以人事档案只在牌面上以"在职人数"这个聚合数出现。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("attendance", "考勤日报", DAILY_PAGE_PATH, PERMISSION));
    }

    /**
     * 告警条：今日缺卡、今日迟到，以及"本月一条汇总都没有"。
     *
     * <p>第三条是本屏最重要的一条：考勤日汇总由重算任务生成，任务没跑时所有数字都是 0，
     * 牌面上看起来像"全勤、零异常"——这比报错更坏。所以"没有任何汇总行"必须自己
     * 说出来，而不是让 0 冒充好成绩。</p>
     *
     * <p>告警条的 count 是 int（契约里它只是"这条告警涉及几件事"），在这里收敛类型，
     * KPI 那边保持 long 不受影响。</p>
     *
     * @param builder 组装器
     * @param todayRows 今日考勤日汇总
     * @param monthRows 本月考勤日汇总
     */
    private static void appendAlerts(Overviews.Builder builder,
            List<ReportRows.AttendanceRow> todayRows, List<ReportRows.AttendanceRow> monthRows) {
        int absentToday = (int) todayRows.stream().filter(row -> value(row.getAbsentDays()) > 0).count();
        int lateToday = (int) todayRows.stream().filter(row -> value(row.getLateDays()) > 0).count();
        if (absentToday > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "今日 " + absentToday + " 人缺卡", absentToday);
        }
        if (lateToday > 0) {
            builder.alert(OverviewAlertLevel.INFO, "今日 " + lateToday + " 人迟到", lateToday);
        }
        if (monthRows.isEmpty()) {
            builder.alert(OverviewAlertLevel.INFO, "本月还没有考勤日汇总，重算任务可能没跑", null);
        }
    }

    /**
     * 计数兜底：null 不当 0 传播到渲染层以外的地方
     *
     * @param value 计数
     * @return 非空计数，缺省 0
     */
    private static long value(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
