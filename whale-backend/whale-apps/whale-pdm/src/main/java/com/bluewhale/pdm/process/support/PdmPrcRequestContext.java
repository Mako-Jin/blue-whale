package com.bluewhale.pdm.process.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 工艺请求上下文取值。
 *
 * <p>用户身份由网关可信注入；缺失时直接拒绝，不回落默认用户。</p>
 *
 * @author BlueWhale
 */
public final class PdmPrcRequestContext {

    /**
     * 工具类禁止实例化
     */
    private PdmPrcRequestContext() {
    }

    /**
     * 读取当前用户 ID。
     *
     * @param request 当前请求
     * @return 用户 ID
     */
    public static Long requireUserId(HttpServletRequest request) {
        String raw = WebHeaders.userId(request);
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "biz.pdm.process.identity-missing-access-via-gateway", "缺少用户身份，请通过网关访问");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "biz.pdm.process.identity-invalid-format", "用户身份格式不正确");
        }
    }
}
