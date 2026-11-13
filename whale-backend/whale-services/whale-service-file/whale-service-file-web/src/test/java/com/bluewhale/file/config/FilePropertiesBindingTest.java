package com.bluewhale.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileProperties} 配置绑定契约测试：锁定配置键与启动期校验行为。
 *
 * @author BlueWhale
 */
class FilePropertiesBindingTest {

    /** 仅启用配置属性绑定，模拟真实启动时的绑定与校验链路 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FileProperties.class)
    static class PropertiesConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    @DisplayName("默认桶名只认 whale.file.bucket.default，default-value 不再参与绑定")
    void bindsDefaultBucketBySingleKey() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "whale.file.bucket.default", "custom",
                "whale.file.bucket.default-value", "ignored")));

        FileProperties properties = binder.bind("whale.file", Bindable.of(FileProperties.class)).get();

        assertThat(properties.getBucket().getDefault()).isEqualTo("custom");
    }

    @Test
    @DisplayName("max-size-bytes 配 0 时启动失败，而不是静默拒绝所有上传")
    void failsFastOnNonPositiveMaxSize() {
        runner.withPropertyValues("whale.file.max-size-bytes=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("max-size-bytes");
                });
    }

    @Test
    @DisplayName("默认配置可正常启动")
    void startsWithDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FileProperties.class).getBucket().getDefault()).isEqualTo("bluewhale");
        });
    }
}
