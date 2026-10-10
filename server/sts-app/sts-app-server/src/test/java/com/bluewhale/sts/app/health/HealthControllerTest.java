package com.bluewhale.sts.app.health;

import com.bluewhale.sts.common.web.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 应用装配自检：验证"能被启动"以及"跨模块约定真的生效"。
 *
 * <p>这类测试的价值不在于覆盖业务逻辑，而在于<b>守住地基</b>——
 * 依赖方向写反、自动配置漏登记、建表脚本没跑，都会在这里立刻暴露，
 * 而不是等到线上才发现。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("健康检查返回统一响应结构，并逐依赖汇报状态")
    void shouldReturnWrappedHealthResult() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("bluewhale-server"))
                // 这里断言 h2 不是随手写的字面量，而是在**钉住默认 profile**：
                // 本项目保证"clone 下来不装任何数据库就能跑"，靠的就是默认走 h2 内存库。
                // 哪天有人把默认改成 mysql，这条会立刻失败 —— 否则这个约定会
                // 悄无声息地失效，直到新人第一次 clone 时才发现跑不起来。
                .andExpect(jsonPath("$.data.profile").value("h2"))
                .andExpect(jsonPath("$.data.dependencies.db").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    @DisplayName("sts-common-web 自动配置生效：全局异常处理器已注册")
    void shouldAutoConfigureGlobalExceptionHandler() {
        assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
    }

    @Test
    @DisplayName("响应体不泄漏 success 字段：业务结果只由 code 表达")
    void shouldNotExposeDerivedSuccessField() throws Exception {
        // R.isSuccess() 若被 Jackson 当成 getter，响应里会凭空多出 "success" 键，
        // 诱导前端写出 if (res.success) 这第二套判断标准。这里把它钉死。
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

}
