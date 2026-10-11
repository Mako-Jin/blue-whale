package com.bluewhale.sts.auth.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bluewhale.sts.auth.authz.AuthInterceptor;
import com.bluewhale.sts.auth.authz.PermissionChecker;
import com.bluewhale.sts.auth.authentication.AuthTokenFilter;
import com.bluewhale.sts.auth.authentication.DefaultAdminSeeder;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.codec.JwtCodec;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.relation.RelRoleResourceMapper;
import com.bluewhale.sts.auth.resource.ResourceMapper;
import com.bluewhale.sts.auth.role.RoleMapper;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.auth.user.UserMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/**
 * 权限域的装配入口。
 *
 * <p>与 {@code LogAutoConfiguration} 同一模式：依赖本模块即自动生效，接入方零配置，
 * 且"本域贡献了哪些组件"写在明面上（而不是靠启动类的 {@code scanBasePackages} 隐式覆盖）。
 *
 * <p><b>{@link ComponentScan} 与 {@link MapperScan} 都严格限制在本模块包内</b>：
 * 自动配置里做范围不明的扫描，可能把别处的 Bean 卷进来、打乱条件装配顺序。
 * Mapper 扫描额外指定 {@code annotationClass = Mapper.class} ——
 * 只有显式标注 {@code @Mapper} 的接口才会被注册，避免包内任何接口都被当成 Mapper。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.bluewhale.sts.auth")
@MapperScan(basePackages = "com.bluewhale.sts.auth", annotationClass = Mapper.class)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    /** JWT 编解码器。密钥为空时构造失败 —— 快速暴露"密钥没配" */
    @Bean
    @ConditionalOnMissingBean
    public JwtCodec jwtCodec(AuthProperties properties, ObjectMapper objectMapper) {
        return new JwtCodec(properties, objectMapper);
    }

    /** 口令哈希器 */
    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher passwordHasher(AuthProperties properties) {
        return new PasswordHasher(properties);
    }

    /** 审计字段自动填充 */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler authAuditMetaObjectHandler() {
        return new AuthAuditMetaObjectHandler();
    }

    /**
     * 注册认证过滤器。
     *
     * <p>用 {@link FilterRegistrationBean} 而不是让 {@code AuthTokenFilter} 直接成为
     * {@code @Bean} 或 {@code @Component}：后者会被 Spring Boot 自动注册到**所有** URL，
     * 包括静态资源与错误页；这里显式限定在 {@code /api/*}，并锁定执行顺序。
     */
    @Bean
    public FilterRegistrationBean<AuthTokenFilter> authTokenFilterRegistration(
            AuthTokenFilter authTokenFilter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(authTokenFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-100);
        registration.setName("authTokenFilter");
        return registration;
    }

    /**
     * 认证过滤器实例。
     *
     * <p>刻意**不加** {@code @Component}：它一旦是组件，就会被上面那条
     * {@link FilterRegistrationBean} 与 Spring Boot 的自动注册**各注册一次**，
     * 过滤器执行两遍 —— 表现为"日志打两次、上下文被清两次"，
     * 排查时很难联想到注册重复。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthTokenFilter authTokenFilter(JwtCodec jwtCodec) {
        return new AuthTokenFilter(jwtCodec);
    }

    /**
     * 注册鉴权拦截器。
     *
     * <p>不加 {@code @ConditionalOnMissingBean}：{@code WebMvcConfigurer} 是**叠加**语义，
     * 应用声明自己的配置类时本配置仍应生效（与 {@code WebAutoConfiguration} 里
     * 跨域配置的判断一致）。条件化会让"应用只要有一个 WebMvcConfigurer，鉴权就静默失效"。
     */
    @Bean
    public WebMvcConfigurer authInterceptorConfigurer(PermissionChecker permissionChecker) {
        AuthInterceptor interceptor = new AuthInterceptor(permissionChecker);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/api/**");
            }
        };
    }

    /**
     * 默认管理员种子数据。
     *
     * <p>由配置开关控制（{@code bluewhale.auth.seed-admin.enabled}），默认关闭、
     * 仅 h2 开发 profile 打开。用 {@link ConditionalOnProperty} 而不是在
     * {@code run()} 里判断，是为了让"种子是否启用"出现在条件装配报告（{@code --debug}）里 ——
     * 运维能一眼看到，而不必读代码。
     */
    @Bean
    @ConditionalOnProperty(prefix = "bluewhale.auth.seed-admin", name = "enabled", havingValue = "true")
    public DefaultAdminSeeder defaultAdminSeeder(AuthProperties properties,
                                                 PasswordHasher passwordHasher,
                                                 TenantMapper tenantMapper,
                                                 UserMapper userMapper,
                                                 ResourceMapper resourceMapper,
                                                 RoleMapper roleMapper,
                                                 RelRoleResourceMapper relRoleResourceMapper,
                                                 RelRoleMemberMapper relRoleMemberMapper) {
        return new DefaultAdminSeeder(properties, passwordHasher, tenantMapper, userMapper,
                resourceMapper, roleMapper, relRoleResourceMapper, relRoleMemberMapper);
    }

}
