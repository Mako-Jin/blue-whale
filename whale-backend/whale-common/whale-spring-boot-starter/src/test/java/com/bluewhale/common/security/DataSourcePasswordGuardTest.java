package com.bluewhale.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataSourcePasswordGuard} 单元测试（P1-2）：空口令拒绝启动、无数据源服务放行。
 *
 * @author BlueWhale
 */
class DataSourcePasswordGuardTest {

    @Test
    @DisplayName("未配置 spring.datasource.url 时放行（网关/监控类应用）")
    void shouldPassWhenUrlAbsent() {
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> new DataSourcePasswordGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("url 为空白时同样视为未配置数据源")
    void shouldPassWhenUrlBlank() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "   ");

        assertThatCode(() -> new DataSourcePasswordGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("有 url 但密码为 null 时拒绝启动，报错含服务名与 MYSQL_PASSWORD")
    void shouldRejectWhenPasswordMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/bluewhale")
                .withProperty("spring.application.name", "whale-service-auth");

        assertThatThrownBy(() -> new DataSourcePasswordGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whale-service-auth")
                .hasMessageContaining("MYSQL_PASSWORD")
                .hasMessageContaining("数据源密码为空");
    }

    @Test
    @DisplayName("密码为空白串时同样拒绝启动，服务名缺失时回退 unknown")
    void shouldRejectWhenPasswordBlankAndNameDefaulted() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:dm://dm:5236/bluewhale")
                .withProperty("spring.datasource.password", "  ");

        assertThatThrownBy(() -> new DataSourcePasswordGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[unknown]");
    }

    @Test
    @DisplayName("url 与密码齐备时正常通过")
    void shouldPassWhenPasswordConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/bluewhale")
                .withProperty("spring.datasource.password", "S3cret!");

        assertThatCode(() -> new DataSourcePasswordGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("S3cret!");
    }
}
