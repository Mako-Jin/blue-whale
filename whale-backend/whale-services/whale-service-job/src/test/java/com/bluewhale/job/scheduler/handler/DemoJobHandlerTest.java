package com.bluewhale.job.scheduler.handler;

import com.bluewhale.job.scheduler.JobHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DemoJobHandler} 示例执行器单元测试。
 *
 * @author BlueWhale
 */
class DemoJobHandlerTest {

    private final JobHandler handler = new DemoJobHandler();

    @Test
    @DisplayName("execute：无参数时返回执行时间且不回显参数")
    void shouldReturnTimestampWithoutParam() throws Exception {
        String result = handler.execute(null);

        assertTrue(result.contains("demoJobHandler executed at"));
        assertFalse(result.contains("param="));
    }

    @Test
    @DisplayName("execute：有参数时原样回显")
    void shouldEchoParam() throws Exception {
        String result = handler.execute("hello");

        assertTrue(result.contains("param=hello"));
    }

    @Test
    @DisplayName("execute：空白参数按无参数处理")
    void shouldIgnoreBlankParam() throws Exception {
        String result = handler.execute("   ");

        assertFalse(result.contains("param="));
    }
}
