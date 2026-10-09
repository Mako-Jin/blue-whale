package com.bluewhale.sts.common.web.config;

import com.bluewhale.sts.common.web.advice.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 公共能力的自动装配入口。
 *
 * <p>被 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 登记，Spring Boot 启动时自动加载 —— 业务模块无需知道本类的存在，也不该依赖包扫描路径。
 *
 * <p><b>为什么不靠启动类的 {@code scanBasePackages} 扩大扫描范围：</b>
 * 那会让"依赖关系"隐式化——代码里看不出 {@code sts-common-web} 到底贡献了什么，
 * 一个包重命名就能静默失效。自动配置把契约写在明面上，是否生效可从
 * 条件装配报告（{@code --debug}）直接看出。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CorsProperties.class)
public class WebAutoConfiguration {

    /**
     * 注册全局异常处理器。
     *
     * <p>带 {@link ConditionalOnMissingBean}：业务方可以声明自己的同类型 Bean 来整体接管，
     * 而不必修改本模块 —— 公共模块的正确姿态是"提供默认值，而非强制值"。
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 跨域支持，仅在 {@code bluewhale.web.cors.enabled=true} 时注册。
     *
     * <p><b>刻意不加 {@link ConditionalOnMissingBean}：</b>{@code WebMvcConfigurer} 是**叠加**语义，
     * 应用声明自己的配置类时，本配置仍然应该生效。若加了这个条件，应用只要有一个
     * {@code WebMvcConfigurer}（很常见），跨域就会静默失效 —— 而失效的表现是
     * "浏览器报跨域错误"，看起来完全不像配置问题。
     */
    @Bean
    @ConditionalOnProperty(prefix = "bluewhale.web.cors", name = "enabled", havingValue = "true")
    public WebMvcConfigurer bluewhaleCorsConfigurer(CorsProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
                        .allowedMethods(properties.getAllowedMethods().toArray(String[]::new))
                        .allowedHeaders(properties.getAllowedHeaders().toArray(String[]::new))
                        .allowCredentials(properties.isAllowCredentials())
                        .maxAge(properties.getMaxAge());
            }
        };
    }

}
