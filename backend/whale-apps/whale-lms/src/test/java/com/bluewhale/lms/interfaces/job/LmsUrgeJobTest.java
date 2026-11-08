package com.bluewhale.lms.interfaces.job;

import com.bluewhale.lms.integration.application.service.UrgeAppService;
import com.bluewhale.lms.integration.application.view.UrgeRunView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 未完成必修催办任务入口测试。
 *
 * @author BlueWhale
 */
class LmsUrgeJobTest {

    private UrgeAppService urgeAppService;

    private LmsUrgeJob job;

    @BeforeEach
    void setUp() {
        urgeAppService = Mockito.mock(UrgeAppService.class);
        job = new LmsUrgeJob(urgeAppService);
    }

    @Test
    @DisplayName("催办成功：返回扫描/提醒摘要")
    void shouldReturnSummary() {
        UrgeRunView view = new UrgeRunView();
        view.setDay(LocalDate.of(2026, 11, 8));
        view.setScannedClasses(12);
        view.setUrgentClasses(3);
        view.setNotified(7);
        view.setFailedClasses(1);
        when(urgeAppService.runScheduled()).thenReturn(view);

        String result = job.urgeDaily();

        assertEquals("day=2026-11-08, scanned=12, urgent=3, notified=7, failed=1", result);
    }

    @Test
    @DisplayName("无当日催办结果：返回 skipped 而非空摘要")
    void shouldReturnSkippedWhenNoResult() {
        when(urgeAppService.runScheduled()).thenReturn(null);

        assertEquals("skipped: no urge result", job.urgeDaily());
    }

    @Test
    @DisplayName("整轮异常上抛：不吞异常，由调度中心记失败")
    void shouldPropagateFailure() {
        when(urgeAppService.runScheduled()).thenThrow(new IllegalStateException("通知服务不可用"));

        assertThrows(IllegalStateException.class, () -> job.urgeDaily());
    }
}
