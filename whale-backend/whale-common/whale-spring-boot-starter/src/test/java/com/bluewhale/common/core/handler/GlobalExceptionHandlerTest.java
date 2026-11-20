package com.bluewhale.common.core.handler;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.i18n.CommonMessageResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GlobalExceptionHandler} 404 语义与国际化解析单元测试。
 *
 * <p>回归背景：路径不存在时 {@code NoResourceFoundException} 落入兜底处理器
 * 被包装成 500，掩盖真实 404（2026-11-08 实机验证暴露，erp/auth 均复现）。</p>
 *
 * @author BlueWhale
 */
class GlobalExceptionHandlerTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    /**
     * 路径不存在应返回 404 业务码，而非 500。
     */
    @Test
    void noResourceFoundShouldReturn404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "not/exist/path", "Not Found");
        Result<Void> result = handler.handleNoResourceFoundException(e);
        assertEquals(ErrorCode.NOT_FOUND.getCode(), result.getCode());
        assertEquals("资源不存在", result.getMessage());
    }

    /**
     * 英文请求下通用错误码应返回英文文案。
     */
    @Test
    @DisplayName("英文 Locale：404 文案来自公共资源包")
    void noResourceFoundShouldReturnEnglish() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.setCommonMessageResolver(new CommonMessageResolver());
        LocaleContextHolder.setLocale(Locale.US);

        Result<Void> result = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "not/exist/path", "Not Found"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), result.getCode());
        assertEquals("Resource not found", result.getMessage());
    }
}
