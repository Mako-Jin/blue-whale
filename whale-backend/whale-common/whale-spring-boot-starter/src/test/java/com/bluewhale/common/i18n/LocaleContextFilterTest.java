package com.bluewhale.common.i18n;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 请求语言上下文过滤器测试。
 */
class LocaleContextFilterTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("X-Lang 优先：链内为英文，请求结束后恢复原语言")
    void shouldUseExplicitHeaderAndRestore() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Lang", "en-US");
        request.addHeader("Accept-Language", "zh-CN");
        AtomicReference<Locale> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(LocaleContextHolder.getLocale());

        Locale before = Locale.SIMPLIFIED_CHINESE;
        LocaleContextHolder.setLocale(before);
        new LocaleContextFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("en", seen.get().getLanguage());
        assertEquals(before, LocaleContextHolder.getLocale());
    }

    @Test
    @DisplayName("无语言头时回落简体中文")
    void shouldDefaultToChinese() throws Exception {
        AtomicReference<Locale> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(LocaleContextHolder.getLocale());

        new LocaleContextFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertEquals(Locale.SIMPLIFIED_CHINESE, seen.get());
    }

    @Test
    @DisplayName("仅 Accept-Language 时按标准协商解析")
    void shouldResolveAcceptLanguage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en-GB,en;q=0.9");
        AtomicReference<Locale> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(LocaleContextHolder.getLocale());

        new LocaleContextFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(Locale.US, seen.get());
    }
}
