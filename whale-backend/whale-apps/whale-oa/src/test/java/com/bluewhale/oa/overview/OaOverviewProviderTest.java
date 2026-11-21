package com.bluewhale.oa.overview;

import com.bluewhale.oa.analytics.domain.model.ReportRows;
import com.bluewhale.oa.analytics.domain.repository.ReportRepository;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.model.OverviewTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OaOverviewProvider} 单元测试：考勤口径与 2D 考勤月报同源，"在职人数"按权限裁剪。
 *
 * @author BlueWhale
 */
class OaOverviewProviderTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);

    private final EmployeeProfileRepository employeeProfileRepository = mock(EmployeeProfileRepository.class);

    private final OaOverviewProvider provider =
            new OaOverviewProvider(reportRepository, employeeProfileRepository);

    /**
     * 构造一行考勤日汇总
     *
     * @param absentDays 缺卡天数
     * @param lateDays 迟到天数
     * @param earlyDays 早退天数
     * @param workDays 出勤天数
     * @return 聚合行
     */
    private static ReportRows.AttendanceRow row(int absentDays, int lateDays, int earlyDays, int workDays) {
        ReportRows.AttendanceRow row = new ReportRows.AttendanceRow();
        row.setUserId(1L);
        row.setUserName("舰长");
        row.setAbsentDays(absentDays);
        row.setLateDays(lateDays);
        row.setEarlyDays(earlyDays);
        row.setWorkDays(workDays);
        return row;
    }

    /**
     * 具备考勤日报与员工档案权限的调用者
     *
     * @return 上下文
     */
    private static OverviewContext context() {
        return OverviewContext.of(1L, 1L, Set.of("sys:oa:attendance:daily:list", "sys:oa:hr:profile:list"));
    }

    /**
     * 按 key 归集 KPI
     *
     * @param snapshot 快照
     * @return key → KPI
     */
    private static Map<String, OverviewKpi> kpis(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    @Test
    @DisplayName("元信息与权限码：复用 2D 考勤日报同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("oa");
        assertThat(provider.nodeKey()).isEqualTo("libu-hall");
        assertThat(provider.title()).isEqualTo("吏部");
        assertThat(provider.subtitle()).isEqualTo("人事考课");
        assertThat(provider.permissionCode()).isEqualTo("sys:oa:attendance:daily:list");
    }

    @Test
    @DisplayName("今日缺卡/迟到数人数，本月出勤数人次：两个单位不能混成一张牌")
    void shouldMapAttendanceToKpis() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        when(reportRepository.attendanceMonthly(monthStart, today))
                .thenReturn(List.of(row(0, 1, 0, 15), row(1, 0, 0, 14), row(0, 0, 0, 15)));
        when(reportRepository.attendanceMonthly(today, today))
                .thenReturn(List.of(row(0, 1, 0, 1), row(1, 0, 0, 1), row(0, 0, 0, 1)));

        Map<String, OverviewKpi> byKey = kpis(provider.build(context()));

        assertThat(byKey).containsOnlyKeys("headcount", "absent", "late", "work-days");
        // 今日：1 人缺卡、1 人迟到
        assertThat(byKey.get("absent").value()).isEqualTo(1d);
        assertThat(byKey.get("absent").tone()).isEqualTo(OverviewTone.BAD);
        assertThat(byKey.get("absent").hint()).isEqualTo("今日在册 3 人");
        assertThat(byKey.get("late").value()).isEqualTo(1d);
        assertThat(byKey.get("late").tone()).isEqualTo(OverviewTone.WARN);
        // 本月：15 + 14 + 15 = 44 人次
        assertThat(byKey.get("work-days").value()).isEqualTo(44d);
        assertThat(byKey.get("work-days").hint()).contains("人次");
    }

    @Test
    @DisplayName("本月异常柱状图：迟到/早退/缺卡 三项为人次之和，类别与数值等长")
    void shouldExposeAbnormalChart() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        when(reportRepository.attendanceMonthly(monthStart, today))
                .thenReturn(List.of(row(1, 2, 3, 10), row(2, 0, 1, 9)));
        when(reportRepository.attendanceMonthly(today, today)).thenReturn(List.of(row(0, 0, 0, 1)));

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.key()).isEqualTo("abnormal");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("迟到", "早退", "缺卡");
        assertThat(chart.series().get(0).values()).containsExactly(2d, 4d, 3d);
        // 契约要求 categories 与 values 等长；不等长时前端只能自己补 0，图就悄悄画歪了
        assertThat(chart.categories()).hasSize(chart.series().get(0).values().size());
    }

    @Test
    @DisplayName("在职人数：档案总数减已离职数，不是只数 ACTIVE")
    void shouldComputeHeadcountAsTotalMinusLeft() {
        LocalDate today = LocalDate.now();
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of(row(0, 0, 0, 1)));
        when(employeeProfileRepository.count(any(EmployeeProfileQuery.class))).thenReturn(120L, 7L);

        OverviewKpi headcount = kpis(provider.build(context())).get("headcount");

        assertThat(headcount.value()).isEqualTo(113d);
        assertThat(headcount.hint()).isEqualTo("按人事档案");
        // 第二次查询必须带 LEFT 条件；不带就会把总数再数一遍，得 120 而不是 113
        ArgumentCaptor<EmployeeProfileQuery> captor = ArgumentCaptor.forClass(EmployeeProfileQuery.class);
        verify(employeeProfileRepository, times(2)).count(captor.capture());
        assertThat(captor.getAllValues().get(1).getEmploymentStatus()).isEqualTo("LEFT");
    }

    @Test
    @DisplayName("在职人数按权限裁剪：缺人事档案权限时连这块牌都不下发")
    void shouldTrimHeadcountByPermission() {
        LocalDate today = LocalDate.now();
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of(row(0, 0, 0, 1)));

        OverviewSnapshot snapshot = provider.build(
                OverviewContext.of(1L, 1L, Set.of("sys:oa:attendance:daily:list")));

        assertThat(snapshot.kpis()).extracting(OverviewKpi::key).containsExactly("absent", "late", "work-days");
        assertThat(snapshot.links()).extracting(OverviewLink::key).containsExactly("attendance");
        // 上下文缺失也不能 500：概览宁可少一块牌
        assertThat(provider.build(null).kpis()).extracting(OverviewKpi::key)
                .containsExactly("absent", "late", "work-days");
    }

    @Test
    @DisplayName("查询区间必须落在自然月内：首查本月一日至今日，次查仅今日")
    void shouldQueryMonthAndToday() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of(row(0, 0, 0, 1)));

        provider.build(context());

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportRepository, atLeastOnce()).attendanceMonthly(from.capture(), to.capture());

        assertThat(from.getAllValues().get(0)).isEqualTo(monthStart);
        assertThat(to.getAllValues().get(0)).isEqualTo(today);
        if (monthStart.equals(today)) {
            // 月初那天两个区间完全重合，不该再扫一遍同一天的数据
            assertThat(from.getAllValues()).hasSize(1);
        } else {
            assertThat(from.getAllValues().get(1)).isEqualTo(today);
            assertThat(to.getAllValues().get(1)).isEqualTo(today);
        }
    }

    @Test
    @DisplayName("本月无汇总行：0 不能冒充全勤，必须自己说出来")
    void shouldWarnWhenMonthRowsAreEmpty() {
        when(reportRepository.attendanceMonthly(any(), any())).thenReturn(List.of());
        when(employeeProfileRepository.count(any(EmployeeProfileQuery.class))).thenReturn(0L);

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("考勤日汇总"));
        assertThat(snapshot.charts().get(0).series().get(0).values()).containsExactly(0d, 0d, 0d);
        assertThat(kpis(snapshot).get("absent").hint()).isEqualTo("今日无汇总");
        assertThat(snapshot.state()).isEqualTo(OverviewState.LIVE);
    }

    @Test
    @DisplayName("告警：今日缺卡为警告、今日迟到为提示，条数各自独立")
    void shouldRaiseAlerts() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        when(reportRepository.attendanceMonthly(monthStart, today))
                .thenReturn(List.of(row(1, 0, 0, 10), row(1, 0, 0, 10), row(2, 0, 0, 9)));
        when(reportRepository.attendanceMonthly(today, today))
                .thenReturn(List.of(row(1, 0, 0, 1), row(1, 1, 0, 1), row(0, 1, 0, 1)));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts()).extracting(OverviewAlert::level)
                .containsExactly(OverviewAlertLevel.WARNING, OverviewAlertLevel.INFO);
        assertThat(snapshot.alerts()).extracting(OverviewAlert::count).containsExactly(2, 2);
    }
}
