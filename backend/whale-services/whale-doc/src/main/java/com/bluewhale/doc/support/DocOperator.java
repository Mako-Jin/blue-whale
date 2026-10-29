package com.bluewhale.doc.support;

import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前操作人上下文（M3 调用工作流时透传可信请求头）。
 *
 * @param userId          用户ID
 * @param username        用户名
 * @param permissionCodes 网关注入的权限码
 * @param roles           用户角色
 * @author BlueWhale
 */
public record DocOperator(Long userId, String username, String permissionCodes, String roles) {

    /**
     * 从当前请求构造操作人上下文。
     *
     * @param request 当前请求
     * @param userId  当前用户ID
     * @return 操作人上下文
     */
    public static DocOperator of(HttpServletRequest request, Long userId) {
        return new DocOperator(userId, WebHeaders.username(request),
                WebHeaders.header(request, WebHeaders.PERMISSION_CODES),
                WebHeaders.header(request, WebHeaders.USER_ROLES));
    }
}
