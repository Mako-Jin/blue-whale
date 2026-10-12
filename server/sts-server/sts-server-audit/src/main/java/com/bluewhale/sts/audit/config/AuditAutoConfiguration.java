package com.bluewhale.sts.audit.config;

import com.bluewhale.sts.audit.recorder.AuditInterceptor;
import com.bluewhale.sts.audit.recorder.AuditRecorder;
import com.bluewhale.sts.common.web.config.WebInterceptorOrder;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 审计域的装配入口。
 *
 * <p>与 {@code LogAutoConfiguration} / {@code AuthAutoConfiguration} 同一模式：
 * 依赖本模块即自动生效，接入方零配置，且"本域贡献了哪些组件"写在明面上
 * （而不是靠启动类的 {@code scanBasePackages} 隐式覆盖）。
 *
 * <p>{@link ComponentScan} 与 {@link MapperScan} 都严格限制在本模块包内，
 * 且 Mapper 扫描额外要求显式 {@code @Mapper} 标注。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.bluewhale.sts.audit")
@MapperScan(basePackages = "com.bluewhale.sts.audit", annotationClass = Mapper.class)
public class AuditAutoConfiguration {

    /**
     * 注册审计拦截器。
     *
     * <p><b>顺序是这里最要紧的一件事</b>，取值见 {@link WebInterceptorOrder}：
     * 必须排在鉴权拦截器之前，否则"某个人试图越权但被拦住"这条记录会静默消失 ——
     * Spring 只对已经成功通过 {@code preHandle} 的拦截器回调 {@code afterCompletion}，
     * 而鉴权拦截器是在 {@code preHandle} 里抛异常的（见 {@code AuthInterceptor}）。
     *
     * <p>作用范围与鉴权拦截器一致（{@code /api/**}），而不是全站：
     * 静态资源、错误页、Swagger UI 都不该产生审计记录。
     *
     * <p>不加 {@code @ConditionalOnMissingBean}：{@code WebMvcConfigurer} 是**叠加**语义，
     * 应用声明自己的配置类时本配置仍应生效。条件化会让"应用只要有一个 WebMvcConfigurer，
     * 审计就静默失效"—— 与 {@code AuthAutoConfiguration} 里对鉴权拦截器的判断一致。
     */
    @Bean
    public WebMvcConfigurer auditInterceptorConfigurer(AuditRecorder auditRecorder) {
        AuditInterceptor interceptor = new AuditInterceptor(auditRecorder);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/api/**")
                        .order(WebInterceptorOrder.AUDIT);
            }
        };
    }

}
