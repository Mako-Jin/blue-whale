package com.bluewhale.overview;

import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.support.Overviews;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WhaleOverviewAutoConfiguration} 装配测试。
 *
 * <p>重点是三件事：① 有 provider 时控制器与服务都在；② 没有 Micrometer 也不会启动失败；
 * ③ 有 MeterRegistry 时 {@code overview.load} 计数真的会涨（降级可观测的唯一证据）。</p>
 *
 * @author BlueWhale
 */
class WhaleOverviewAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WhaleOverviewAutoConfiguration.class))
            .withBean(OverviewProvider.class, FixedProvider::new);

    @Test
    @DisplayName("注册服务与控制器（路径取配置）")
    void shouldRegisterBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OverviewService.class);
            assertThat(context).hasSingleBean(OverviewController.class);
            assertThat(context).hasSingleBean(OverviewProperties.class);
        });
    }

    @Test
    @DisplayName("whale.overview.enabled=false 时整体不装配")
    void shouldSkipWhenDisabled() {
        runner.withPropertyValues("whale.overview.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(OverviewService.class));
    }

    @Test
    @DisplayName("有 MeterRegistry 时装载结果进 overview.load 计数")
    void shouldCountOutcomes() {
        runner.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            OverviewService service = context.getBean(OverviewService.class);
            OverviewProvider provider = context.getBean(OverviewProvider.class);

            service.load(OverviewContext.of(1L, 1L, Set.of()), provider);

            assertThat(registry.get("overview.load").tag("service", "erp").tag("outcome", "live").counter().count()).isEqualTo(1d);
        });
    }

    @Test
    @DisplayName("路径来自配置：base-path 绑定到 OverviewProperties（控制器注解取的是这个值）")
    void shouldBindConfiguredBasePath() {
        runner.withPropertyValues("whale.overview.base-path=/erp/overview").run(context ->
                assertThat(context.getBean(OverviewProperties.class).getBasePath()).isEqualTo("/erp/overview"));
    }

    @Test
    @DisplayName("没有 MeterRegistry 时退化为空实现，不抛异常")
    void shouldWorkWithoutMeterRegistry() {
        runner.run(context -> {
            OverviewService service = context.getBean(OverviewService.class);
            OverviewProvider provider = context.getBean(OverviewProvider.class);

            OverviewSnapshot snapshot = service.load(OverviewContext.of(1L, 1L, Set.of()), provider);

            assertThat(snapshot.state().getValue()).isEqualTo("live");
        });
    }

    /**
     * 固定 provider：不依赖任何下游。
     */
    private static final class FixedProvider implements OverviewProvider {

        @Override
        public String service() {
            return "erp";
        }

        @Override
        public String nodeKey() {
            return "hubu-hall";
        }

        @Override
        public String title() {
            return "户部";
        }

        @Override
        public OverviewSnapshot build(OverviewContext context) {
            return Overviews.builder("erp", "hubu-hall", "户部")
                    .kpi(Overviews.count("orders", "订单", 1))
                    .build();
        }
    }
}
