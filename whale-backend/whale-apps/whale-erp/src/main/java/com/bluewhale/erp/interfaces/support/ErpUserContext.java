package com.bluewhale.erp.interfaces.support;

import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从网关注入的身份头解析当前操作人。
 *
 * <p>服务自身不解析 JWT、不查询 auth 库；身份与权限判定结果只信任网关。</p>
 *
 * @author BlueWhale
 */
public final class ErpUserContext {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private ErpUserContext() {
    }

    /**
     * 解析当前用户 ID（缺失或非法时返回 {@code null}）。
     *
     * @param request 当前请求
     * @return 用户 ID
     */
    public static Long userId(HttpServletRequest request) {
        String text = WebHeaders.userId(request);
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
