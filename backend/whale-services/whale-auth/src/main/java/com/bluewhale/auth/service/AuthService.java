package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.auth.dto.LoginRequest;
import com.bluewhale.auth.dto.RefreshRequest;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.security.LoginUser;
import com.bluewhale.auth.security.TokenBlacklist;
import com.bluewhale.auth.security.UserDetailsServiceImpl;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.common.core.exception.BizException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务：登录（BCrypt 校验）、刷新 Token、登出
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final SysUserMapper userMapper;
    private final TokenBlacklist tokenBlacklist;

    /**
     * 登录：AuthenticationManager 走 UserDetailsService + BCryptPasswordEncoder 校验
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new BizException(401, "用户名或密码错误");
        } catch (DisabledException e) {
            throw new BizException(401, "账号已停用");
        } catch (AuthenticationException e) {
            log.warn("认证失败: username={}, reason={}", request.getUsername(), e.getMessage());
            throw new BizException(401, "认证失败");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        // 记录最后登录时间
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, loginUser.getUserId())
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));
        return buildLoginResponse(loginUser);
    }

    /**
     * 刷新：refreshToken 换发新的 accessToken + refreshToken
     */
    public LoginResponse refresh(RefreshRequest request) {
        Claims claims = tokenProvider.parse(request.getRefreshToken());
        if (claims == null || !JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            throw new BizException(401, "refreshToken 无效或已过期");
        }
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
        } catch (UsernameNotFoundException e) {
            throw new BizException(401, "refreshToken 无效或已过期");
        }
        if (!userDetails.isEnabled()) {
            throw new BizException(401, "账号已停用");
        }
        return buildLoginResponse((LoginUser) userDetails);
    }

    /**
     * 登出：将当前 accessToken 写入分布式黑名单，使其在剩余有效期内立即失效。
     * <p>TTL 取 token 剩余有效期，到期后由 Redis 自动清理；
     * Redis 不可用时降级为客户端丢弃 token，不阻断登出流程。</p>
     *
     * @param authorization 请求头原始值，形如 {@code Bearer <token>}；缺失或格式不符时静默跳过
     */
    public void logout(String authorization) {
        String token = resolveBearerToken(authorization);
        if (token == null) {
            log.debug("登出请求未携带有效 Bearer token，跳过黑名单写入");
            return;
        }
        tokenBlacklist.add(token);
    }

    /**
     * 从 Authorization 头提取 token 原文
     *
     * @param authorization 请求头原始值
     * @return token 原文；头缺失、前缀不符或 token 段为空时返回 {@code null}
     */
    private static String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(JwtTokenProvider.BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(JwtTokenProvider.BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private LoginResponse buildLoginResponse(LoginUser loginUser) {
        LoginResponse response = new LoginResponse();
        List<String> roles = loginUser.getRoles();
        response.setAccessToken(tokenProvider.generateAccessToken(loginUser.getUserId(), loginUser.getUsername(), roles));
        response.setRefreshToken(tokenProvider.generateRefreshToken(loginUser.getUserId(), loginUser.getUsername()));
        response.setTokenType("Bearer");
        response.setExpiresIn(tokenProvider.getAccessExpireSeconds());
        response.setUsername(loginUser.getUsername());
        response.setNickname(loginUser.getNickname());
        response.setRoles(roles);
        return response;
    }
}
