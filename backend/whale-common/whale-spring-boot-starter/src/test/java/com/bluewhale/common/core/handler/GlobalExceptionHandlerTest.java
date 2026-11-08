package com.bluewhale.common.core.handler;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GlobalExceptionHandler} 404 语义单元测试。
 *
 * <p>回归背景：路径不存在时 {@code NoResourceFoundException} 落入兜底处理器
 * 被包装成 500，掩盖真实 404（2026-11-08 实机验证暴露，erp/auth 均复现）。</p>
 *
 * @author BlueWhale
 */
class GlobalExceptionHandlerTest {

    /**
     * 路径不存在应返回 404 业务码，而非 500。
     */
    @Test
    void noResourceFoundShouldReturn404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "not/exist/path", "Not Found");
        Result<Void> result = handler.handleNoResourceFoundException(e);
        assertEquals(ErrorCode.NOT_FOUND.getCode(), result.getCode());
    }
}
