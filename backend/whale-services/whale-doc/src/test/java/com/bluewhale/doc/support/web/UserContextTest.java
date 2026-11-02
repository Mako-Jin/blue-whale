package com.bluewhale.doc.support.web;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link UserContext} 请求头解析与未认证拒绝断言。
 *
 * @author BlueWhale
 */
class UserContextTest {

    @Test
    void requireUserIdShouldParseTrustedHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(WebHeaders.USER_ID)).thenReturn(" 42 ");

        assertThat(UserContext.requireUserId(request)).isEqualTo(42L);
    }

    @Test
    void requireUserIdShouldRejectBlankHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(WebHeaders.USER_ID)).thenReturn("   ");

        assertThatThrownBy(() -> UserContext.requireUserId(request))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void requireUserIdShouldRejectNonNumericHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(WebHeaders.USER_ID)).thenReturn("not-a-number");

        assertThatThrownBy(() -> UserContext.requireUserId(request))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.UNAUTHORIZED.getCode());
    }
}
