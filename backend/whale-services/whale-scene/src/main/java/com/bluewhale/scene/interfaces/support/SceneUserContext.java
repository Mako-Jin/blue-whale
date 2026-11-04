package com.bluewhale.scene.interfaces.support;

import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.scene.application.support.NavigationUser;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 从网关注入的身份头解析导航访问者。
 *
 * <p>身份与权限判定结果只信任网关：服务自身不解析 JWT、不查询 auth 库。</p>
 *
 * @author BlueWhale
 */
public final class SceneUserContext {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private SceneUserContext() {
    }

    /**
     * 解析当前访问者。
     *
     * @param request 当前请求
     * @return 访问者身份
     */
    public static NavigationUser navigationUser(HttpServletRequest request) {
        Set<String> roles = WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.USER_ROLES));
        Set<String> permissions = WebHeaders.splitCodes(
                WebHeaders.header(request, WebHeaders.PERMISSION_CODES));
        return new NavigationUser(parseUserId(WebHeaders.userId(request)), roles, permissions, null);
    }

    /**
     * 解析用户 ID（网关未注入或非法时返回 {@code null}）。
     *
     * @param userIdText 用户 ID 文本
     * @return 用户 ID
     */
    private static Long parseUserId(String userIdText) {
        if (userIdText == null) {
            return null;
        }
        try {
            return Long.valueOf(userIdText);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
