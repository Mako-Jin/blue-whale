package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.auth.dto.LoginRequest;
import com.bluewhale.auth.dto.RefreshRequest;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.security.LoginUser;
import com.bluewhale.auth.security.PermissionInterceptor;
import com.bluewhale.auth.security.TokenBlacklist;
import com.bluewhale.auth.security.UserDetailsServiceImpl;
import com.bluewhale.auth.vo.AuthMeResponse;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 认证服务：登录（BCrypt 校验）、刷新 Token、登出
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** refreshToken 无效 / 过期 / 命中黑名单时的统一对外文案，避免泄露具体失败原因 */
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "refreshToken 无效或已过期";
    /** 已登出令牌被再次使用时的对外文案 */
    private static final String REVOKED_REFRESH_TOKEN_MESSAGE = "refreshToken 已失效，请重新登录";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final SysUserMapper userMapper;
    private final TokenBlacklist tokenBlacklist;
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final SysPermissionService permissionService;

    /**
     * 登录：AuthenticationManager 走 UserDetailsService + BCryptPasswordEncoder 校验
     *
     * @param request 登录请求（用户名与明文密码）
     * @return 登录响应（含 accessToken 与 refreshToken）
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
     * 刷新：refreshToken 换发新的 accessToken + refreshToken。
     * <p>先做签名/过期与令牌类型校验，再查分布式黑名单：命中（已在登出时失效）直接 401，
     * 阻断「登出后仍可用 refreshToken 换发新 token」的提权路径（REQ-SEC-004）。</p>
     *
     * @param request 刷新请求（含 refreshToken）
     * @return 登录响应（含新 accessToken 与 refreshToken）
     */
    public LoginResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        Claims claims = tokenProvider.parse(refreshToken);
        if (claims == null || !JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            throw new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (tokenBlacklist.contains(refreshToken)) {
            log.debug("refreshToken 命中登出黑名单，拒绝换发新令牌: username={}", claims.getSubject());
            throw new BizException(401, REVOKED_REFRESH_TOKEN_MESSAGE);
        }
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
        } catch (UsernameNotFoundException e) {
            throw new BizException(401, INVALID_REFRESH_TOKEN_MESSAGE);
        }
        if (!userDetails.isEnabled()) {
            throw new BizException(401, "账号已停用");
        }
        return buildLoginResponse((LoginUser) userDetails);
    }

    /**
     * 登出：将 accessToken 与 refreshToken 一并写入分布式黑名单，使其在剩余有效期内立即失效。
     * <p>TTL 取各自令牌剩余有效期，到期后由 Redis 自动清理；
     * Redis 不可用时降级为客户端丢弃 token，不阻断登出流程。</p>
     *
     * @param authorization 请求头原始值，形如 {@code Bearer <token>}；缺失或格式不符时静默跳过
     * @param refreshToken  请求体携带的 refreshToken（可空）；为空时仅失效 accessToken
     */
    public void logout(String authorization, String refreshToken) {
        String accessToken = resolveBearerToken(authorization);
        if (accessToken != null) {
            tokenBlacklist.add(accessToken);
        } else {
            log.debug("登出请求未携带有效 Bearer token，跳过 accessToken 黑名单写入");
        }
        if (StringUtils.hasText(refreshToken)) {
            tokenBlacklist.add(refreshToken);
        } else {
            log.debug("登出请求未携带 refreshToken，仅失效 accessToken");
        }
    }

    /**
     * 当前登录用户信息：角色 / 权限码 / 可见菜单树。
     * <p>权限码与菜单为「动态路由 + 按钮级权限渲染」的数据源：内置超管（super_admin）
     * 授予全量启用权限码与全部启用可见菜单，其余用户按「用户 → 角色 → 权限点」实际授权过滤。</p>
     *
     * @return 当前登录用户的角色、权限码与可见菜单树
     */
    public AuthMeResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        List<String> roles = loginUser.getRoles();
        boolean superAdmin = roles.contains(PermissionInterceptor.SUPER_ADMIN);
        List<String> permissions = superAdmin
                ? permissionService.listAllCodes()
                : roleService.listPermissionCodesByUserId(loginUser.getUserId());
        List<MenuVO> menus = superAdmin
                ? menuService.treeByPermissionCodes(null)
                : menuService.treeByPermissionCodes(Set.copyOf(permissions));
        AuthMeResponse response = new AuthMeResponse();
        response.setUserId(loginUser.getUserId());
        response.setUsername(loginUser.getUsername());
        response.setNickname(loginUser.getNickname());
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(menus);
        return response;
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

    /**
     * 为用户签发 accessToken 与 refreshToken 并组装登录响应
     *
     * @param loginUser 登录用户
     * @return 登录响应（含令牌、有效期与用户基础信息）
     */
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
