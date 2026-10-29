package com.bluewhale.doc.support.web;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 文档服务的当前用户解析。
 *
 * <p>用户身份来自网关可信注入的 {@code X-User-Id}；文档服务不自行解析 JWT。</p>
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
     * 读取当前用户ID，缺失或非法时按未认证拒绝。
     *
     * @param request 当前请求
     * @return 用户ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        String raw = WebHeaders.userId(request);
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
