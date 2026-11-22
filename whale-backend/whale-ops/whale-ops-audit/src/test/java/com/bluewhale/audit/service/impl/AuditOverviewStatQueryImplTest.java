package com.bluewhale.audit.service.impl;

import com.bluewhale.audit.mapper.stat.AuditDayCountPo;
import com.bluewhale.audit.mapper.stat.AuditOperateTypePo;
import com.bluewhale.audit.mapper.stat.AuditOverviewStatMapper;
import com.bluewhale.audit.retention.mapper.AuditJobHeartbeatMapper;
import com.bluewhale.audit.retention.po.AuditJobHeartbeatPo;
import com.bluewhale.audit.service.AuditOverviewStatQuery.DayAudit;
import com.bluewhale.audit.service.AuditOverviewStatQuery.OperateTypeCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 起居殿概览统计查询单测：钉住时间窗与空值兜底口径。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditOverviewStatQueryImplTest {

    private static final long TENANT = 1L;
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 22, 0, 0);

    @Mock
    private AuditOverviewStatMapper statMapper;

    @Mock
    private AuditJobHeartbeatMapper heartbeatMapper;

    @InjectMocks
    private AuditOverviewStatQueryImpl query;

    @Test
    @DisplayName("无效时间窗：租户/起止缺失或起不早于止时一律返回空值，不打 SQL")
    void shouldReturnEmptyForInvalidWindow() {
        assertEquals(0L, query.todayCount(null, FROM, TO));
        assertEquals(0L, query.todayCount(TENANT, null, TO));
        assertEquals(0L, query.todayCount(TENANT, FROM, null));
        assertEquals(0L, query.todayCount(TENANT, TO, FROM));
        assertEquals(0L, query.failedCount(TENANT, TO, TO));
        assertEquals(List.of(), query.dailyAudits(TENANT, TO, FROM));
        assertEquals(List.of(), query.topOperateTypes(TENANT, FROM, TO, 5));
    }

    @Test
    @DisplayName("有效时间窗：今日数与失败数直接走聚合 SQL")
    void shouldCountWithinValidWindow() {
        when(statMapper.countBetween(TENANT, FROM, TO)).thenReturn(12L);
        when(statMapper.countFailedBetween(TENANT, FROM, TO)).thenReturn(3L);

        assertEquals(12L, query.todayCount(TENANT, FROM, TO));
        assertEquals(3L, query.failedCount(TENANT, FROM, TO));
    }

    @Test
    @DisplayName("按天聚合：跳过空日期行，空计数按 0 兜底")
    void shouldMapDailyAuditsWithNullFallback() {
        AuditDayCountPo nullDay = new AuditDayCountPo();
        nullDay.setAudits(9L);
        AuditDayCountPo nullCount = new AuditDayCountPo();
        nullCount.setDay(LocalDate.of(2026, 11, 21));
        when(statMapper.countByDay(TENANT, FROM, TO)).thenReturn(List.of(nullDay, nullCount));

        List<DayAudit> rows = query.dailyAudits(TENANT, FROM, TO);

        assertEquals(1, rows.size());
        assertEquals(LocalDate.of(2026, 11, 21), rows.get(0).day());
        assertEquals(0L, rows.get(0).audits());
    }

    @Test
    @DisplayName("操作类型 TopN：空/空白归 OTHER，正常类型保留，空计数按 0 兜底")
    void shouldMapTopOperateTypes() {
        AuditOperateTypePo nullType = new AuditOperateTypePo();
        nullType.setAudits(5L);
        AuditOperateTypePo blankType = new AuditOperateTypePo();
        blankType.setOperateType("  ");
        AuditOperateTypePo normalType = new AuditOperateTypePo();
        normalType.setOperateType("LOGIN");
        when(statMapper.topOperateTypes(TENANT, FROM, TO, 3))
                .thenReturn(List.of(nullType, blankType, normalType));

        List<OperateTypeCount> rows = query.topOperateTypes(TENANT, FROM, TO, 3);

        assertEquals(3, rows.size());
        assertEquals("OTHER", rows.get(0).operateType());
        assertEquals(5L, rows.get(0).audits());
        assertEquals("OTHER", rows.get(1).operateType());
        assertEquals("LOGIN", rows.get(2).operateType());
        assertEquals(0L, rows.get(2).audits());
    }

    @Test
    @DisplayName("limit 非正数：TopN 直接返回空表，不打 SQL")
    void shouldRejectNonPositiveLimit() {
        assertEquals(List.of(), query.topOperateTypes(TENANT, FROM, TO, 0));
        assertEquals(List.of(), query.topOperateTypes(TENANT, FROM, TO, -1));
    }

    @Test
    @DisplayName("最近审计时间：无租户返回 null，有效租户取聚合值")
    void shouldReadLastAuditTime() {
        assertNull(query.lastAuditTime(null));
        when(statMapper.lastAuditTime(TENANT)).thenReturn(TO);

        assertEquals(TO, query.lastAuditTime(TENANT));
    }

    @Test
    @DisplayName("归档心跳：无记录返回 null，有记录取最近成功时刻")
    void shouldReadArchiveLastSuccessAt() {
        assertNull(query.archiveLastSuccessAt());

        AuditJobHeartbeatPo heartbeat = new AuditJobHeartbeatPo();
        heartbeat.setLastSuccessAt(FROM);
        when(heartbeatMapper.selectOne(any())).thenReturn(heartbeat);

        assertEquals(FROM, query.archiveLastSuccessAt());
    }
}
