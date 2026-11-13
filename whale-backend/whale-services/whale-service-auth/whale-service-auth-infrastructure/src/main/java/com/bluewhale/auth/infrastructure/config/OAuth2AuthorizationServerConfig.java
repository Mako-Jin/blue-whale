package com.bluewhale.auth.infrastructure.config;

import com.bluewhale.auth.domain.repository.OAuthClientRepository;
import com.bluewhale.auth.infrastructure.security.JwtAuthenticationFilter;
import com.bluewhale.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.bluewhale.auth.infrastructure.security.oauth2.OAuth2ClientRegisteredClientAdapter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2.0 授权服务器装配（M1，Spring Security 7 内置 Spring Authorization Server）。
 *
 * <p><b>端点</b>：沿用协议标准路径（{@code /oauth2/authorize|token|revoke|introspect|jwks} 与
 * {@code /.well-known/oauth-authorization-server}）。协议端点不参与业务 API 的
 * {@code /资源/v1} 版本约定，保持标准路径可最大化与三方客户端（Postman / SPA / SDK）的互通性。</p>
 *
 * <p><b>令牌同构</b>：签名密钥与既有登录令牌完全一致（{@code whale.auth.jwt.secret}，HS256），
 * 声明由 {@code OAuth2TokenClaimsCustomizer} 补齐，因此资源侧无需区分令牌来源。</p>
 *
 * <p><b>存储</b>：授权码 / 令牌状态落在 {@code oauth2_authorization}（Flyway V17），
 * 多实例共享同一数据库，避免内存态在多副本下丢失。</p>
 *
 * @author BlueWhale
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizationServerConfig {

    private final OAuth2ServerProperties oauth2ServerProperties;
    private final JwtProperties jwtProperties;

    /**
     * 授权服务器过滤链：优先于业务过滤链，仅匹配协议端点。
     *
     * <p>令牌端点由框架在链内完成客户端认证，因此匿名请求不会被
     * {@code authenticated()} 拦截；{@code /oauth2/authorize} 需要已登录用户，
     * 未认证时返回统一 401 JSON（由前端引导重新登录）。</p>
     *
     * @param http Spring Security 构建器
     * @param authenticationEntryPoint 统一 401 出口
     * @param jwtAuthenticationFilter 既有 JWT 过滤器（同构令牌可直接识别）
     * @return 授权服务器过滤链
     * @throws Exception 构建失败时抛出
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();
        http
                .securityMatcher(configurer.getEndpointsMatcher())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .with(configurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 注册客户端仓储：以 {@code sys_oauth_client} 为唯一事实源
     *
     * @param oauthClientRepository 领域客户端仓储
     * @return 框架侧注册客户端仓储
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuthClientRepository oauthClientRepository) {
        return new OAuth2ClientRegisteredClientAdapter(oauthClientRepository);
    }

    /**
     * JWK 源：与业务令牌共用 HS256 密钥
     *
     * @return HS256 JWK 源
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        SecretKey secretKey = new SecretKeySpec(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new ImmutableSecret<>(secretKey);
    }

    /**
     * JWT 解码器：供令牌内省端点校验签名
     *
     * @param jwkSource JWK 源
     * @return JWT 解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * 授权服务器元数据：issuer 参与发现文档与令牌 iss 声明
     *
     * @return 授权服务器设置
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(oauth2ServerProperties.getIssuer())
                .build();
    }

    /**
     * 授权状态存储（授权码 / 令牌 / state）
     *
     * @param dataSource 数据源
     * @param registeredClientRepository 注册客户端仓储
     * @return 授权服务
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(DataSource dataSource,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(new JdbcTemplate(dataSource), registeredClientRepository);
    }

    /**
     * 授权确认存储
     *
     * @param dataSource 数据源
     * @param registeredClientRepository 注册客户端仓储
     * @return 授权确认服务
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            DataSource dataSource, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(new JdbcTemplate(dataSource), registeredClientRepository);
    }
}
