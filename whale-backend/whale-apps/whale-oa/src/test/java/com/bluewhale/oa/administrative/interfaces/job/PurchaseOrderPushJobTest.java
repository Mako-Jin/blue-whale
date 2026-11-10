package com.bluewhale.oa.administrative.interfaces.job;

import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 采购申请 ERP 推送任务入口测试。
 *
 * @author BlueWhale
 */
class PurchaseOrderPushJobTest {

    private PurchaseAppService purchaseAppService;

    private PurchaseOrderPushJob job;

    @BeforeEach
    void setUp() {
        purchaseAppService = Mockito.mock(PurchaseAppService.class);
        job = new PurchaseOrderPushJob(purchaseAppService);
    }

    @Test
    @DisplayName("推送开关关闭：返回 skipped，不调应用服务")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        assertEquals("skipped: purchase push disabled", job.push());
        Mockito.verifyNoInteractions(purchaseAppService);
    }

    @Test
    @DisplayName("推送开关开启：返回推送成功条数")
    void shouldReturnPushedCount() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(purchaseAppService.pushPending()).thenReturn(4);

        assertEquals("pushed=4", job.push());
    }
}
