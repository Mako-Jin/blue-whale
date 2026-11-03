package com.bluewhale.video.interfaces.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 视频服务的当前用户解析。
 *
 * <p>用户身份来自网关可信注入的 {@code X-User-Id}；视频服务不自行解析 JWT。</p>
 *
 * @author BlueWhale
 */
public final class UserContext {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private UserContext() {
    }

    /**
     * 读取当前用户 ID，缺失或非法时按未认证拒绝。
     *
     * @param request 当前请求
     * @return 用户 ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        Long userId = optionalUserId(request);
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 读取当前用户 ID，缺失或非法时返回 {@code null}（用于可匿名访问的静态资源代理）。
     *
     * @param request 当前请求
     * @return 用户 ID；未认证返回 {@code null}
     */
    public static Long optionalUserId(HttpServletRequest request) {
        String raw = WebHeaders.userId(request);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
