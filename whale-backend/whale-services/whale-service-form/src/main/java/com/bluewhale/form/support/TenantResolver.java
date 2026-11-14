package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 租户解析工具：由网关注入的请求头 {@code X-Tenant-Id} 解析行级租户 ID。
 *
 * <p>Wave 5.54 Phase 0：租户头由网关从 JWT 可信注入，服务端不再回落默认租户。
 * 缺失视为租户上下文异常（{@code PARAM_ERROR}），非法（非数字）同样抛 {@code PARAM_ERROR}。</p>
 *
 * @author BlueWhale
 */
public final class TenantResolver {

    /** 工具类不允许实例化 */
    private TenantResolver() {
    }

    /**
     * 从请求头解析租户ID
     *
     * @param request 当前请求
     * @return 租户ID
     * @throws BizException 头缺失或非数字时抛出 {@code PARAM_ERROR}
     */
    public static Long resolve(HttpServletRequest request) {
        String raw = WebHeaders.tenantId(request);
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少租户上下文");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "租户ID格式非法");
        }
    }
}
