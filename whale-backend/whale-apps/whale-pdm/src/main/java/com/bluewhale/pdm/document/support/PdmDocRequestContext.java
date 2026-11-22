package com.bluewhale.pdm.document.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 图文档请求上下文取值
 *
 * <p>用户与角色由网关可信注入；缺失用户直接拒绝，不回落默认用户。</p>
 *
 * @author BlueWhale
 */
public final class PdmDocRequestContext {

    /**
     * 工具类禁止实例化
     */
    private PdmDocRequestContext() {
    }

    /**
     * 读取当前用户 ID
     *
     * @param request 当前请求
     * @return 用户 ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        String raw = WebHeaders.userId(request);
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "biz.pdm.document.identity-missing-access-via-gateway", "缺少用户身份，请通过网关访问");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "biz.pdm.document.identity-invalid-format", "用户身份格式不正确");
        }
    }

    /**
     * 读取当前用户名
     *
     * @param request 当前请求
     * @return 用户名，缺失返回 {@code null}
     */
    public static String username(HttpServletRequest request) {
        return WebHeaders.username(request);
    }

    /**
     * 当前用户角色集合
     *
     * @param request 当前请求
     * @return 角色编码集合
     */
    public static Set<String> roles(HttpServletRequest request) {
        return WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.USER_ROLES));
    }

    /**
     * 是否可管理他人文档
     *
     * @param request 当前请求
     * @return true 表示可跨文档管理
     */
    public static boolean canManage(HttpServletRequest request) {
        return PdmDocRoles.canManage(roles(request));
    }
}
