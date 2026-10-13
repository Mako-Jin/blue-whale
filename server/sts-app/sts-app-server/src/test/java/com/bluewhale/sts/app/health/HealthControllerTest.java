package com.bluewhale.sts.app.health;

import com.bluewhale.sts.common.web.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

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

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("健康检查返回统一响应结构，并逐依赖汇报状态")
    void shouldReturnWrappedHealthResult() throws Exception {
        // 上报的 profile 必须与**本次真正生效的** profile 一致。
        //
        // 这里刻意不写 "h2"：本类的作用是证明"接口报的是真话"，而"默认 profile 是 h2"
        // 是另一回事（那是一条**配置事实**，由下面 defaultProfileMustBeH2 直接读
        // application.yml 来钉）。两者混在一起写过一次，后果是
        // `mvn verify -Dspring.profiles.active=mysql` 会红在这里，
        // 而红的原因与本类要守的东西毫无关系 —— 见 docs/decisions/0038。
        String[] activeProfiles = environment.getActiveProfiles();
        String expectedProfile = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("bluewhale-server"))
                .andExpect(jsonPath("$.data.profile").value(expectedProfile))
                .andExpect(jsonPath("$.data.dependencies.db").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    @DisplayName("默认 profile 必须是 h2：clone 下来不装任何数据库就能跑")
    @SuppressWarnings("unchecked")
    void defaultProfileMustBeH2() throws Exception {
        // 本项目对外的承诺之一是"不装数据库也能起"。守它的方式有讲究：
        // 不能靠"断言本次运行的 profile"（那是启动参数，与承诺无关），
        // 只能读 application.yml —— 那份文件里的 `spring.profiles.active` 才是承诺本身。
        // 所以这个测试**直接读 yml**，与 DatasourceDialectTest 同一套做法：
        // 配置改了测试就要跟着红，而不是测试里另抄一份默认值。
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> profiles = (Map<String, Object>) spring.get("profiles");

            assertThat(profiles.get("active"))
                    .as("application.yml 的默认 profile 被改掉了，"
                            + "\"clone 下来不装数据库就能跑\"这条承诺随之失效")
                    .isEqualTo("h2");
        }
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
