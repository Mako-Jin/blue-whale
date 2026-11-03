package com.bluewhale.auth.security;

import com.bluewhale.auth.application.auth.port.TokenBlacklist;
import com.bluewhale.auth.application.auth.port.UserAccessRevocationPort;
import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.security.token.TokenTimeSupport;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

/**
 * JWT 认证过滤器：解析 Bearer token → 构造 UsernamePasswordAuthenticationToken → SecurityContext。
 * <p>无效 token 不在过滤器层抛 401，直接放行链路，
 * 未认证请求由 ExceptionHandling（RestAuthenticationEntryPoint）统一返回 401 Result JSON。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 强制改密状态下仍允许访问的路径（自助改密 / 登出 / 刷新 / 当前用户） */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED_PATHS =
            Set.of("/auth/password", "/auth/logout", "/auth/refresh", "/auth/me");

    /** 强制改密拦截的对外文案与状态码（REQ-SEC-023，前端据此弹出改密对话框） */
    private static final int PASSWORD_CHANGE_REQUIRED = 403;
    private static final String PASSWORD_CHANGE_REQUIRED_MESSAGE = "首次登录或口令已到期，请先修改口令";

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklist tokenBlacklist;
    private final TenantMembershipRevocationStore membershipRevocationStore;
    private final UserAccessRevocationPort userAccessRevocationStore;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(JwtTokenProvider.BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (!authenticate(header.substring(JwtTokenProvider.BEARER_PREFIX.length()), request, response)) {
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 校验 token 并写入 SecurityContext。
     * <p>以下情况一律保持未认证状态（由 EntryPoint 返回 401），不在此处直接写响应：
     * token 无效/过期、非 accessToken、命中登出黑名单、租户成员关系已解绑且令牌签发于解绑前、
     * 用户已停用/删除/改密且令牌签发于撤销前、用户不存在或已停用。</p>
     *
     * @param token   已剥离 Bearer 前缀的 token 原文
     * @param request 当前请求，用于填充认证详情
     * @param response 当前响应，强制改密拦截时直接写出 403
     * @return 继续链路返回 true；已写出 403 响应返回 false
     * @throws IOException 写 403 响应失败时抛出
     */
    private boolean authenticate(String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Claims claims = tokenProvider.parse(token);
        // 仅 accessToken 可访问 API，refreshToken 需走 /auth/refresh 换取新 token
        if (claims == null || !JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            return true;
        }
        if (tokenBlacklist.contains(token)) {
            log.debug("token 命中登出黑名单，按未认证处理: username={}", claims.getSubject());
            return true;
        }
        Long tenantId = resolveTenantId(claims);
        Long userId = resolveUserId(claims);
        if (tenantId == null || userId == null) {
            log.debug("accessToken 缺少租户或用户 claim，按未认证处理: username={}", claims.getSubject());
            return true;
        }
        // REQ-SEC-023：强制改密/口令到期时仅放行改密、登出、刷新与当前用户接口（auth 侧第一层拦截）
        if (requiresPasswordChange(claims)
                && !PASSWORD_CHANGE_ALLOWED_PATHS.contains(request.getRequestURI())) {
            log.info("令牌携带强制改密标记，拒绝访问受保护资源: path={}, userId={}",
                    request.getRequestURI(), userId);
            writePasswordChangeRequired(response);
            return false;
        }
        Date issuedAt = new Date(TokenTimeSupport.issuedAtMillis(claims));
        // 一人多租户：成员解绑后即时拒绝解绑前签发的本租户令牌，无需等待过期
        if (membershipRevocationStore.isRevoked(tenantId, userId, issuedAt)) {
            log.debug("token 签发于租户成员解绑之前，按未认证处理: tenantId={}, userId={}", tenantId, userId);
            return true;
        }
        // 用户停用/删除/改密：跨租户撤销全部存量令牌
        if (userAccessRevocationStore.isRevoked(userId, issuedAt)) {
            log.debug("token 签发于用户级撤销之前，按未认证处理: userId={}", userId);
            return true;
        }
        try {
            // 一人多租户：令牌租户可能不是账号首页租户，按 userId 跨租户加载并按令牌租户解析角色
            UserDetails userDetails = userDetailsService.loadUserById(userId, tenantId);
            if (!userDetails.isEnabled()) {
                return true;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ignored) {
            // token 对应的用户不存在，保持未认证状态，由 EntryPoint 返回 401
        }
        return true;
    }

    /**
     * 判断 accessToken 是否携带强制改密标记
     *
     * @param claims 令牌载荷
     * @return 需要强制改密返回 true
     */
    private static boolean requiresPasswordChange(Claims claims) {
        Object raw = claims.get(JwtTokenProvider.CLAIM_PWD_CHANGE_REQUIRED);
        return raw != null && Boolean.parseBoolean(String.valueOf(raw));
    }

    /**
     * 写出 403 强制改密响应（Result JSON），供前端弹出改密对话框
     *
     * @param response 当前响应
     * @throws IOException 写响应失败时抛出
     */
    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(PASSWORD_CHANGE_REQUIRED, PASSWORD_CHANGE_REQUIRED_MESSAGE)));
    }

    /**
     * 从 accessToken 载荷解析租户 ID
     *
     * @param claims 令牌载荷
     * @return 租户 ID；缺失或非法时返回 {@code null}
     */
    private static Long resolveTenantId(Claims claims) {
        return resolveLongClaim(claims, JwtTokenProvider.CLAIM_TENANT_ID);
    }

    /**
     * 从 accessToken 载荷解析用户 ID
     *
     * @param claims 令牌载荷
     * @return 用户 ID；缺失或非法时返回 {@code null}
     */
    private static Long resolveUserId(Claims claims) {
        return resolveLongClaim(claims, JwtTokenProvider.CLAIM_USER_ID);
    }

    /**
     * 解析数值型 claim
     *
     * @param claims 令牌载荷
     * @param claimName claim 名称
     * @return 数值；缺失或非法时返回 {@code null}
     */
    private static Long resolveLongClaim(Claims claims, String claimName) {
        Object raw = claims.get(claimName);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
