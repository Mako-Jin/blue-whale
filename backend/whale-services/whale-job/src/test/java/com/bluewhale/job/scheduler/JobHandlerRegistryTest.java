package com.bluewhale.job.scheduler;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JobHandlerRegistry} 执行器解析与存在性判定单元测试。
 *
 * @author BlueWhale
 */
class JobHandlerRegistryTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);

    private final JobHandlerRegistry registry = new JobHandlerRegistry(applicationContext);

    @Test
    @DisplayName("resolve：合法 Bean 返回执行器实例")
    void shouldResolveHandler() {
        JobHandler handler = param -> "ok";
        when(applicationContext.containsBean("demoJobHandler")).thenReturn(true);
        when(applicationContext.getBean("demoJobHandler")).thenReturn(handler);

        assertSame(handler, registry.resolve("demoJobHandler"));
    }

    @Test
    @DisplayName("resolve：Bean 名为空抛参数错误")
    void shouldRejectBlankBeanName() {
        BizException ex = assertThrows(BizException.class, () -> registry.resolve(" "));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("resolve：Bean 不存在抛参数错误")
    void shouldRejectMissingBean() {
        when(applicationContext.containsBean("missing")).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> registry.resolve("missing"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("resolve：Bean 未实现 JobHandler 抛参数错误")
    void shouldRejectWrongType() {
        when(applicationContext.containsBean("notHandler")).thenReturn(true);
        when(applicationContext.getBean("notHandler")).thenReturn(new Object());

        BizException ex = assertThrows(BizException.class, () -> registry.resolve("notHandler"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("exists：合法执行器返回真")
    void shouldReportExistsTrue() {
        when(applicationContext.containsBean("demoJobHandler")).thenReturn(true);
        when(applicationContext.getBean("demoJobHandler")).thenReturn((JobHandler) param -> "ok");

        assertTrue(registry.exists("demoJobHandler"));
    }

    @Test
    @DisplayName("exists：空名、缺失、类型不符均为假")
    void shouldReportExistsFalse() {
        when(applicationContext.containsBean("x")).thenReturn(true);
        when(applicationContext.getBean("x")).thenReturn(new Object());

        assertFalse(registry.exists(null));
        assertFalse(registry.exists("missing"));
        assertFalse(registry.exists("x"));
    }
}
