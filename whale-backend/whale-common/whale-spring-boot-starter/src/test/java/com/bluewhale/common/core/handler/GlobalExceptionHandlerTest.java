package com.bluewhale.common.core.handler;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.i18n.CommonMessageResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpMethod;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /**
     * 构造带单个字段错误的参数校验异常。
     */
    private static MethodArgumentNotValidException validationException(String defaultMessage)
            throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", defaultMessage));
        return new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0),
                bindingResult);
    }

    /** 供反射取 MethodParameter 的占位方法 */
    private static void dummy(String value) {
    }

    @Test
    @DisplayName("校验消息 {key} 形式按 Locale 从消息源解析")
    void validationMessageKeyShouldBeLocalized() throws NoSuchMethodException {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.setCommonMessageResolver(new CommonMessageResolver());

        LocaleContextHolder.setLocale(Locale.US);
        assertEquals("This field is required",
                handler.handleMethodArgumentNotValidException(validationException("{common.validation.required}"))
                        .getMessage());

        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertEquals("此项为必填",
                handler.handleMethodArgumentNotValidException(validationException("{common.validation.required}"))
                        .getMessage());
    }

    @Test
    @DisplayName("校验消息为纯文本时原样返回（兼容 DTO 硬编码文案）")
    void validationPlainTextShouldPassThrough() throws NoSuchMethodException {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.setCommonMessageResolver(new CommonMessageResolver());
        LocaleContextHolder.setLocale(Locale.US);

        assertEquals("名称不能为空",
                handler.handleMethodArgumentNotValidException(validationException("名称不能为空")).getMessage());
    }

    @Test
    @DisplayName("未绑定 LocaleContext 时回落中文，不取 JVM 默认语言")
    void missingLocaleContextShouldFallbackToChinese() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.setCommonMessageResolver(new CommonMessageResolver());
        LocaleContextHolder.resetLocaleContext();

        assertEquals("资源不存在", handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "not/exist/path", "Not Found")).getMessage());
    }
}
