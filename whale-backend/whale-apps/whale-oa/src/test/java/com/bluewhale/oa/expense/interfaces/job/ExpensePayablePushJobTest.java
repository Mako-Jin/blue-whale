package com.bluewhale.oa.expense.interfaces.job;

import com.bluewhale.oa.expense.application.service.ExpenseAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 报销单 ERP 应付推送任务入口测试。
 *
 * @author BlueWhale
 */
class ExpensePayablePushJobTest {

    private ExpenseAppService expenseAppService;

    private ExpensePayablePushJob job;

    @BeforeEach
    void setUp() {
        expenseAppService = Mockito.mock(ExpenseAppService.class);
        job = new ExpensePayablePushJob(expenseAppService);
    }

    @Test
    @DisplayName("推送开关关闭：返回 skipped，不调应用服务")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        assertEquals("skipped: expense push disabled", job.push());
        Mockito.verifyNoInteractions(expenseAppService);
    }

    @Test
    @DisplayName("推送开关开启：返回推送成功条数")
    void shouldReturnPushedCount() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(expenseAppService.pushPending()).thenReturn(2);

        assertEquals("pushed=2", job.push());
    }
}
