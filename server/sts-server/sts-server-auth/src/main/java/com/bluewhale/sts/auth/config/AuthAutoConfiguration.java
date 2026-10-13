package com.bluewhale.sts.auth.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bluewhale.sts.auth.authz.AuthInterceptor;
import com.bluewhale.sts.auth.authz.PermissionChecker;
import com.bluewhale.sts.auth.authentication.AuthTokenFilter;
import com.bluewhale.sts.auth.authentication.DefaultAdminSeeder;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.LoginLockPolicy;
import com.bluewhale.sts.auth.common.PasswordPolicy;
import com.bluewhale.sts.auth.common.codec.JwtCodec;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.organization.OrganizationMapper;
import com.bluewhale.sts.auth.organization.OrganizationMemberService;
import com.bluewhale.sts.auth.organization.OrganizationService;
import com.bluewhale.sts.auth.relation.RelOrgUserMapper;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.relation.RelRoleResourceMapper;
import com.bluewhale.sts.auth.resource.ResourceMapper;
import com.bluewhale.sts.auth.role.RoleMapper;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.auth.user.UserMapper;
import com.bluewhale.sts.common.web.config.WebInterceptorOrder;
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

    /** 口令强度策略。无状态、纯函数式，供用户管理与认证两处共用 */
    @Bean
    @ConditionalOnMissingBean
    public PasswordPolicy passwordPolicy(AuthProperties properties) {
        return new PasswordPolicy(properties);
    }

    /** 登录锁定与退避策略。同样无状态，把"锁多久"的算术从 AuthService 里挪出来以便单独测 */
    @Bean
    @ConditionalOnMissingBean
    public LoginLockPolicy loginLockPolicy(AuthProperties properties) {
        return new LoginLockPolicy(properties);
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
     *
     * <p><b>顺序必须排在审计拦截器之后</b>（{@link WebInterceptorOrder#AUTHORIZATION}
     * 大于 {@link WebInterceptorOrder#AUDIT}）。理由不是风格：鉴权失败是在本拦截器的
     * {@code preHandle} 里抛异常，而 Spring 只对**已经成功执行过 {@code preHandle}** 的
     * 拦截器回调 {@code afterCompletion}。若本拦截器排在审计之前，"越权被拒"这件事
     * 就永远不会被写成审计记录 —— 不报错、不告警，只是查不到。
     */
    @Bean
    public WebMvcConfigurer authInterceptorConfigurer(PermissionChecker permissionChecker) {
        AuthInterceptor interceptor = new AuthInterceptor(permissionChecker);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/api/**")
                        .order(WebInterceptorOrder.AUTHORIZATION);
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
     *
     * <p>依赖里有两个是**服务**（组织域）而不是 Mapper —— 组织节点的
     * {@code orgLevel} / {@code codePath} / {@code fullName} 是派生列，
     * 种子复用 {@code OrganizationService.create} 而不是自己拼一遍，
     * 免得同一套规则存在两份、而其中一份没有测试盯着（理由见该方法的注释）。
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
                                                 RelRoleMemberMapper relRoleMemberMapper,
                                                 OrganizationService organizationService,
                                                 OrganizationMemberService organizationMemberService,
                                                 OrganizationMapper organizationMapper,
                                                 RelOrgUserMapper relOrgUserMapper) {
        return new DefaultAdminSeeder(properties, passwordHasher, tenantMapper, userMapper,
                resourceMapper, roleMapper, relRoleResourceMapper, relRoleMemberMapper,
                organizationService, organizationMemberService, organizationMapper, relOrgUserMapper);
    }

}
