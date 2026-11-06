package com.bluewhale.oa.hr.interfaces.job;

import com.bluewhale.oa.hr.application.service.TripReportReminderAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TripReportReminderJob} 开关、宽限期与批量参数测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TripReportReminderJobTest {

    @Mock
    private TripReportReminderAppService tripReportReminderAppService;

    private TripReportReminderJob job;

    /**
     * 构造任务实例并注入配置项。
     */
    @BeforeEach
    void setUp() {
        job = new TripReportReminderJob(tripReportReminderAppService);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "daysAfter", 3L);
        ReflectionTestUtils.setField(job, "batchSize", 100);
    }

    @Test
    @DisplayName("关闭开关时不调用应用服务")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.remind();

        verifyNoInteractions(tripReportReminderAppService);
    }

    @Test
    @DisplayName("按宽限期计算截止日并传递批量参数")
    void shouldRemindWithDeadlineAndBatchSize() {
        when(tripReportReminderAppService.remindOverdueReports(
                LocalDate.now().minusDays(3), 100)).thenReturn(2);

        job.remind();

        verify(tripReportReminderAppService).remindOverdueReports(LocalDate.now().minusDays(3), 100);
    }

    @Test
    @DisplayName("宽限期为负时按零天处理")
    void shouldClampNegativeDaysAfter() {
        ReflectionTestUtils.setField(job, "daysAfter", -5L);

        job.remind();

        verify(tripReportReminderAppService).remindOverdueReports(LocalDate.now(), 100);
    }
}
