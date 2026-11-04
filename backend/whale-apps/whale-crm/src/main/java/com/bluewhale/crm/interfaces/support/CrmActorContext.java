package com.bluewhale.crm.interfaces.support;

import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.common.CrmActor;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从网关可信身份头构建 {@link CrmActor}。
 *
 * <p>服务不解析 JWT；若请求绕过网关、缺少 {@code X-User-Id}，则构建匿名操作人，
 * 由应用服务在执行写操作时失败关闭。</p>
 *
 * @author BlueWhale
 */
public final class CrmActorContext {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private CrmActorContext() {
    }

    /**
     * 解析当前操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    public static CrmActor from(HttpServletRequest request) {
        if (request == null) {
            return CrmActor.anonymous();
        }
        Long userId = parseId(WebHeaders.userId(request));
        return new CrmActor(userId,
                WebHeaders.username(request),
                WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.USER_ROLES)),
                WebHeaders.splitCodes(WebHeaders.header(request, WebHeaders.PERMISSION_CODES)));
    }

    /**
     * 解析长整型 ID。
     *
     * @param value 文本
     * @return 合法正数 ID；非法返回 null
     */
    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
