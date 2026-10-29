package com.bluewhale.common.web;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.security.RequirePermissionInterceptor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 请求用户上下文工具：统一从 {@link RequestContextHolder} 或网关请求头解析调用者用户 ID。
 *
 * <p>优先读取过滤器已绑定的请求上下文，未绑定（如单元测试、非过滤器链路）时回落到
 * {@code X-User-Id} 请求头，保证各服务取用户口径一致，避免重复实现解析与异常文案。</p>
 *
 * @author BlueWhale
 */
public final class UserContext {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private UserContext() {
    }

    /**
     * 读取当前用户 ID，缺失或非法时抛出 401 业务异常
     *
     * @param request 当前请求
     * @return 用户 ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        Long contextUserId = RequestContextHolder.getUserId();
        if (contextUserId != null) {
            return contextUserId;
        }
        String raw = WebHeaders.userId(request);
        if (raw == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户标识，请通过网关登录后访问");
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户标识非法");
        }
    }

    /**
     * 读取当前用户 ID，缺失或非法时返回 {@code null}
     *
     * @param request 当前请求
     * @return 用户 ID；无有效值时返回 {@code null}
     */
    public static Long findUserId(HttpServletRequest request) {
        Long contextUserId = RequestContextHolder.getUserId();
        if (contextUserId != null) {
            return contextUserId;
        }
        String raw = WebHeaders.userId(request);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断当前请求是否具备指定权限码（含内置超管放行）
     *
     * <p>取值口径与 {@link RequirePermissionInterceptor} 对齐：优先权限码头
     * {@code X-Permission-Codes}，缺失时回落角色头 {@code X-User-Roles}；
     * 任一集合含 {@code super_admin} 即视为具备全部权限。用于需要在应用/接口层做
     * 数据可见范围裁剪的场景（档案密级、文件归属等），与注解式接口准入互为补充。</p>
     *
     * @param request 当前请求
     * @param code 权限码，为空时返回 {@code false}
     * @return 具备该权限码时返回 {@code true}
     */
    public static boolean hasPermission(HttpServletRequest request, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String raw = WebHeaders.header(request, WebHeaders.PERMISSION_CODES);
        if (raw == null) {
            raw = WebHeaders.header(request, WebHeaders.USER_ROLES);
        }
        Set<String> codes = WebHeaders.splitCodes(raw);
        return codes.contains(RequirePermissionInterceptor.SUPER_ADMIN) || codes.contains(code);
    }
}
