package com.bluewhale.agent.support.web;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求用户上下文：从网关注入的 {@code X-User-Id} 解析当前用户。
 *
 * @author BlueWhale
 */
public final class UserContext {

    /** 工具类不允许实例化 */
    private UserContext() {
    }

    /**
     * 读取当前用户ID，缺失或非法时抛出业务异常。
     *
     * @param request 当前请求
     * @return 用户ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        String userId = WebHeaders.userId(request);
        if (userId == null) {
            throw new BizException("缺少用户标识，请通过网关登录后访问");
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BizException("用户标识非法");
        }
    }
}
