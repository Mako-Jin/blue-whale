package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(JwtTokenProvider.BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(JwtTokenProvider.BEARER_PREFIX.length()), request);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 校验 token 并写入 SecurityContext。
     * <p>以下情况一律保持未认证状态（由 EntryPoint 返回 401），不在此处直接写响应：
     * token 无效/过期、非 accessToken、命中登出黑名单、用户不存在或已停用。</p>
     *
     * @param token   已剥离 Bearer 前缀的 token 原文
     * @param request 当前请求，用于填充认证详情
     */
    private void authenticate(String token, HttpServletRequest request) {
        Claims claims = tokenProvider.parse(token);
        // 仅 accessToken 可访问 API，refreshToken 需走 /auth/refresh 换取新 token
        if (claims == null || !JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            return;
        }
        if (tokenBlacklist.contains(token)) {
            log.debug("token 命中登出黑名单，按未认证处理: username={}", claims.getSubject());
            return;
        }
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
            if (!userDetails.isEnabled()) {
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ignored) {
            // token 对应的用户不存在，保持未认证状态，由 EntryPoint 返回 401
        }
    }
}
