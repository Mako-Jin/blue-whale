package com.bluewhale.scene.interfaces.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
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
        return new NavigationUser(parseNumeric(WebHeaders.userId(request)), roles, permissions, null);
    }

    /**
     * 读取当前用户 ID，缺失或非法时按未认证拒绝。
     *
     * @param request 当前请求
     * @return 用户 ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        Long userId = parseNumeric(WebHeaders.userId(request));
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 读取当前租户 ID，缺失或非法时按未认证拒绝。
     *
     * <p>偏好读写落在 auth 统一配置表（按 tenant_id 行级隔离），缺租户上下文会串租户，
     * 因此与用户 ID 同样失败关闭——宁可让请求失败，也不写错租户。</p>
     *
     * @param request 当前请求
     * @return 租户 ID
     */
    public static Long requireTenantId(HttpServletRequest request) {
        Long tenantId = parseNumeric(WebHeaders.tenantId(request));
        if (tenantId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return tenantId;
    }

    /**
     * 解析网关注入的数值型头部（缺失或非法时返回 {@code null}）。
     *
     * @param text 头部文本
     * @return 解析结果
     */
    private static Long parseNumeric(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
