package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 租户解析工具：由请求头 {@code X-Tenant-Id} 解析行级租户 ID。
 *
 * <p>平台当前无租户模型，故取值优先读请求头（客户端可选传递）；缺失回落默认租户
 * {@link #DEFAULT_TENANT_ID}；非法（非数字）抛 {@code PARAM_ERROR}。</p>
 *
 * @author BlueWhale
 */
public final class TenantResolver {

    /** 默认租户ID */
    public static final long DEFAULT_TENANT_ID = 1L;

    /** 工具类不允许实例化 */
    private TenantResolver() {
    }

    /**
     * 从请求头解析租户ID
     *
     * @param request 当前请求
     * @return 租户ID；缺省回落 {@link #DEFAULT_TENANT_ID}
     * @throws BizException 头值非数字时抛出 {@code PARAM_ERROR}
     */
    public static Long resolve(HttpServletRequest request) {
        String raw = WebHeaders.tenantId(request);
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_TENANT_ID;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "租户ID格式非法");
        }
    }
}
