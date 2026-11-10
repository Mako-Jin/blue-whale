package com.bluewhale.doc.support;

import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DocOperator} 请求头透传断言。
 *
 * @author BlueWhale
 */
class DocOperatorTest {

    @Test
    void ofShouldCopyTrustedHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(WebHeaders.USERNAME)).thenReturn("  alice  ");
        when(request.getHeader(WebHeaders.PERMISSION_CODES)).thenReturn("doc:read,doc:write");
        when(request.getHeader(WebHeaders.USER_ROLES)).thenReturn("admin");

        DocOperator operator = DocOperator.of(request, 11L);

        assertThat(operator.userId()).isEqualTo(11L);
        assertThat(operator.username()).isEqualTo("alice");
        assertThat(operator.permissionCodes()).isEqualTo("doc:read,doc:write");
        assertThat(operator.roles()).isEqualTo("admin");
    }

    @Test
    void ofShouldReturnNullsForMissingHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        DocOperator operator = DocOperator.of(request, null);

        assertThat(operator.userId()).isNull();
        assertThat(operator.username()).isNull();
        assertThat(operator.permissionCodes()).isNull();
        assertThat(operator.roles()).isNull();
    }

    @Test
    void ofShouldNormalizeBlankHeadersToNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(WebHeaders.USERNAME)).thenReturn("   ");
        when(request.getHeader(WebHeaders.PERMISSION_CODES)).thenReturn("");
        when(request.getHeader(WebHeaders.USER_ROLES)).thenReturn(" reader ");

        DocOperator operator = DocOperator.of(request, 1L);

        assertThat(operator.username()).isNull();
        assertThat(operator.permissionCodes()).isNull();
        assertThat(operator.roles()).isEqualTo("reader");
    }
}
