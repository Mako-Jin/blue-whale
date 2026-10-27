package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.WhaleGatewayProperties;
import com.bluewhale.gateway.security.MembershipRevocationChecker;
import com.bluewhale.gateway.security.TenantStatusChecker;
import com.bluewhale.gateway.security.TokenBlacklistChecker;
import com.bluewhale.gateway.security.UserAccessRevocationChecker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthGlobalFilter} 单元测试：黑名单命中 401、正常放行注入身份头、
 * 拒绝 refreshToken 冒充访问令牌（REQ-SEC-003 / T5.7）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthGlobalFilterTest {

    /** 与 WhaleGatewayProperties 默认约定一致的测试密钥（≥ 32 字节） */
    private static final String SECRET = "unit-test-secret-key-with-enough-length-0123456789";
    private static final String USERNAME = "alice";
    private static final long USER_ID = 1251323419503984640L;
    private static final String ROLES = "admin,user";
    private static final long TENANT_ID = 1L;
    private static final long TOKEN_TTL_MILLIS = 60_000L;
    private static final String PROTECTED_PATH = "http://localhost/auth/users";

    @Mock
    private TokenBlacklistChecker tokenBlacklistChecker;

    @Mock
    private TenantStatusChecker tenantStatusChecker;

    @Mock
    private MembershipRevocationChecker membershipRevocationChecker;

    @Mock
    private UserAccessRevocationChecker userAccessRevocationChecker;

    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        WhaleGatewayProperties properties = new WhaleGatewayProperties();
        properties.getJwt().setSecret(SECRET);
        filter = new JwtAuthGlobalFilter(properties, tokenBlacklistChecker, tenantStatusChecker,
                membershipRevocationChecker, userAccessRevocationChecker);
        lenient().when(membershipRevocationChecker.isRevoked(anyString(), anyString(), any()))
                .thenReturn(Mono.just(Boolean.FALSE));
        lenient().when(userAccessRevocationChecker.isRevoked(anyString(), any()))
                .thenReturn(Mono.just(Boolean.FALSE));
    }

    private static String token(String tokenType) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(USERNAME)
                .claim("userId", USER_ID)
                .claim("tenantId", TENANT_ID)
                .claim("roles", ROLES)
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_TTL_MILLIS))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static MockServerWebExchange exchangeWithBearer(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
    }

    @Test
    @DisplayName("filter：令牌命中登出黑名单时返回 401，且不转发下游")
    void shouldReturnUnauthorizedWhenTokenBlacklisted() {
        String accessToken = token("access");
        MockServerWebExchange exchange = exchangeWithBearer(accessToken);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(tokenBlacklistChecker.isBlacklisted(accessToken)).thenReturn(Mono.just(Boolean.TRUE));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：租户不可用（停用/到期）时返回 401，且不转发下游")
    void shouldReturnUnauthorizedWhenTenantDisabled() {
        String accessToken = token("access");
        MockServerWebExchange exchange = exchangeWithBearer(accessToken);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(tokenBlacklistChecker.isBlacklisted(accessToken)).thenReturn(Mono.just(Boolean.FALSE));
        when(tenantStatusChecker.isUnavailable(String.valueOf(TENANT_ID))).thenReturn(Mono.just(Boolean.TRUE));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：未命中黑名单时放行，并注入 X-User-Id / X-Username / X-User-Roles / X-Tenant-Id")
    void shouldPassThroughAndInjectIdentityHeadersWhenNotBlacklisted() {
        String accessToken = token("access");
        MockServerWebExchange exchange = exchangeWithBearer(accessToken);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(tokenBlacklistChecker.isBlacklisted(accessToken)).thenReturn(Mono.just(Boolean.FALSE));
        when(tenantStatusChecker.isUnavailable(anyString())).thenReturn(Mono.just(Boolean.FALSE));
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertEquals(String.valueOf(USER_ID), headers.getFirst("X-User-Id"));
        assertEquals(USERNAME, headers.getFirst("X-Username"));
        assertEquals(ROLES, headers.getFirst("X-User-Roles"));
        assertEquals(String.valueOf(TENANT_ID), headers.getFirst("X-Tenant-Id"));
    }

    @Test
    @DisplayName("filter：客户端伪造的 X-Tenant-Id 被 JWT 租户覆盖")
    void shouldOverrideForgedTenantHeader() {
        String accessToken = token("access");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Tenant-Id", "999")
                .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(tokenBlacklistChecker.isBlacklisted(accessToken)).thenReturn(Mono.just(Boolean.FALSE));
        when(tenantStatusChecker.isUnavailable(anyString())).thenReturn(Mono.just(Boolean.FALSE));
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals(String.valueOf(TENANT_ID),
                captor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"));
    }

    @Test
    @DisplayName("filter：缺少 tenantId claim 的旧令牌返回 401")
    void shouldRejectTokenWithoutTenantClaim() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .subject(USERNAME)
                .claim("userId", USER_ID)
                .claim("roles", ROLES)
                .claim("tokenType", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_TTL_MILLIS))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        MockServerWebExchange exchange = exchangeWithBearer(legacyToken);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：白名单路径清除客户端伪造的身份与租户头")
    void shouldStripForgedIdentityHeadersForWhiteListPath() {
        WhaleGatewayProperties properties = new WhaleGatewayProperties();
        properties.getJwt().setSecret(SECRET);
        properties.setWhiteList(java.util.List.of("/auth/login"));
        JwtAuthGlobalFilter whiteListFilter = new JwtAuthGlobalFilter(properties, tokenBlacklistChecker,
                tenantStatusChecker, membershipRevocationChecker, userAccessRevocationChecker);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("http://localhost/auth/login")
                .header("X-Tenant-Id", "999")
                .header("X-User-Id", "888")
                .header("X-Username", "forged")
                .header("X-User-Roles", "admin")
                .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        whiteListFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertEquals(null, headers.getFirst("X-Tenant-Id"));
        assertEquals(null, headers.getFirst("X-User-Id"));
        assertEquals(null, headers.getFirst("X-Username"));
        assertEquals(null, headers.getFirst("X-User-Roles"));
    }

    @Test
    @DisplayName("filter：租户成员关系已解除时返回 401，且不转发下游")
    void shouldReturnUnauthorizedWhenMembershipRevoked() {
        String accessToken = token("access");
        MockServerWebExchange exchange = exchangeWithBearer(accessToken);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(tokenBlacklistChecker.isBlacklisted(accessToken)).thenReturn(Mono.just(Boolean.FALSE));
        when(tenantStatusChecker.isUnavailable(String.valueOf(TENANT_ID))).thenReturn(Mono.just(Boolean.FALSE));
        when(membershipRevocationChecker.isRevoked(eq(String.valueOf(TENANT_ID)),
                eq(String.valueOf(USER_ID)), any())).thenReturn(Mono.just(Boolean.TRUE));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：用户已停用/删除/改密时返回 401，且不转发下游")
    void shouldReturnUnauthorizedWhenUserAccessRevoked() {
        String accessToken = token("access");
        MockServerWebExchange exchange = exchangeWithBearer(accessToken);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(tokenBlacklistChecker.isBlacklisted(accessToken)).thenReturn(Mono.just(Boolean.FALSE));
        when(tenantStatusChecker.isUnavailable(String.valueOf(TENANT_ID))).thenReturn(Mono.just(Boolean.FALSE));
        when(userAccessRevocationChecker.isRevoked(eq(String.valueOf(USER_ID)), any()))
                .thenReturn(Mono.just(Boolean.TRUE));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：refreshToken 冒充 accessToken 访问时返回 401，且不查黑名单")
    void shouldRejectRefreshTokenUsedAsAccessToken() {
        MockServerWebExchange exchange = exchangeWithBearer(token("refresh"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain, tokenBlacklistChecker);
    }

    @Test
    @DisplayName("filter：签名不符的令牌返回 401")
    void shouldRejectTokenWithInvalidSignature() {
        WhaleGatewayProperties otherProperties = new WhaleGatewayProperties();
        otherProperties.getJwt().setSecret("another-unit-test-secret-key-9876543210-abcdefgh");
        JwtAuthGlobalFilter otherFilter = new JwtAuthGlobalFilter(otherProperties, tokenBlacklistChecker,
                tenantStatusChecker, membershipRevocationChecker, userAccessRevocationChecker);
        MockServerWebExchange exchange = exchangeWithBearer(token("access"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        otherFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }
}
