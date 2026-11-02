package com.bluewhale.doc.ws;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.support.collab.DocWsTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocWsHandshakeInterceptor} Origin 校验、ticket 消费与拒绝状态码断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocWsHandshakeInterceptorTest {

    @Mock
    private DocCollabService collabService;

    private final DocProperties docProperties = new DocProperties();

    private DocWsHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DocWsHandshakeInterceptor(collabService, docProperties);
    }

    private ServerHttpRequest request(String query, String origin) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/doc/ws" + (query == null ? "" : "?" + query)));
        HttpHeaders headers = new HttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    private DocWsTicket ticket() {
        return new DocWsTicket(3L, 7L, 9L, "alice");
    }

    @Test
    void shouldConsumeTicketAndStoreAttributes() {
        docProperties.setCollabAllowedOrigins("*");
        when(collabService.consumeTicket("tk")).thenReturn(ticket());
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean passed = interceptor.beforeHandshake(request("ticket=tk&clientId=client-01", "https://a.com"),
                response, mock(org.springframework.web.socket.WebSocketHandler.class), attributes);

        assertThat(passed).isTrue();
        assertThat(attributes.get(DocCollabAttributes.TICKET)).isInstanceOf(DocWsTicket.class);
        assertThat(attributes.get(DocCollabAttributes.CLIENT_ID)).isEqualTo("client-01");
        verify(response, never()).setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectIllegalClientId() {
        when(collabService.consumeTicket("tk")).thenReturn(ticket());
        Map<String, Object> attributes = new HashMap<>();

        interceptor.beforeHandshake(request("ticket=tk&clientId=short", null), mock(ServerHttpResponse.class),
                mock(org.springframework.web.socket.WebSocketHandler.class), attributes);

        assertThat(attributes.get(DocCollabAttributes.CLIENT_ID)).isNull();
    }

    @Test
    void shouldRejectTooLongClientId() {
        when(collabService.consumeTicket("tk")).thenReturn(ticket());
        Map<String, Object> attributes = new HashMap<>();
        String tooLong = "c".repeat(65);

        interceptor.beforeHandshake(request("ticket=tk&clientId=" + tooLong, null),
                mock(ServerHttpResponse.class), mock(org.springframework.web.socket.WebSocketHandler.class), attributes);

        assertThat(attributes.get(DocCollabAttributes.CLIENT_ID)).isNull();
    }

    @Test
    void shouldAllowRequestWithoutQueryAndConsumeNullTicket() {
        when(collabService.consumeTicket(null)).thenReturn(ticket());
        Map<String, Object> attributes = new HashMap<>();

        assertThat(interceptor.beforeHandshake(request(null, null), mock(ServerHttpResponse.class),
                mock(org.springframework.web.socket.WebSocketHandler.class), attributes)).isTrue();
        assertThat(attributes.get(DocCollabAttributes.CLIENT_ID)).isNull();
    }

    @Test
    void shouldRejectOriginOutsideAllowList() {
        docProperties.setCollabAllowedOrigins("https://a.com, https://b.com");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean passed = interceptor.beforeHandshake(request("ticket=tk", "https://evil.com"), response,
                mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>());

        assertThat(passed).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(collabService, never()).consumeTicket(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldAllowOriginInTrimmedAllowListIgnoringCase() {
        docProperties.setCollabAllowedOrigins(" https://A.com , https://b.com ");
        when(collabService.consumeTicket("tk")).thenReturn(ticket());

        assertThat(interceptor.beforeHandshake(request("ticket=tk", "https://a.com"),
                mock(ServerHttpResponse.class), mock(org.springframework.web.socket.WebSocketHandler.class),
                new HashMap<>())).isTrue();
    }

    @Test
    void shouldAllowMissingOriginWithAllowList() {
        docProperties.setCollabAllowedOrigins("https://a.com");
        when(collabService.consumeTicket("tk")).thenReturn(ticket());

        assertThat(interceptor.beforeHandshake(request("ticket=tk", null), mock(ServerHttpResponse.class),
                mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>())).isTrue();
    }

    @Test
    void shouldAllowWhenAllowListBlank() {
        docProperties.setCollabAllowedOrigins("   ");
        when(collabService.consumeTicket("tk")).thenReturn(ticket());

        assertThat(interceptor.beforeHandshake(request("ticket=tk", "https://evil.com"),
                mock(ServerHttpResponse.class), mock(org.springframework.web.socket.WebSocketHandler.class),
                new HashMap<>())).isTrue();
    }

    @Test
    void shouldReturnServiceUnavailableWhenCollabUnavailable() {
        when(collabService.consumeTicket("tk")).thenThrow(DocErrorCode.exception(DocErrorCode.COLLAB_UNAVAILABLE));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertThat(interceptor.beforeHandshake(request("ticket=tk", null), response,
                mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldReturnUnauthorizedWhenTicketInvalid() {
        when(collabService.consumeTicket("tk")).thenThrow(DocErrorCode.exception(DocErrorCode.COLLAB_TICKET_INVALID));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertThat(interceptor.beforeHandshake(request("ticket=tk", null), response,
                mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnServiceUnavailableOnUnexpectedRuntimeException() {
        when(collabService.consumeTicket("tk")).thenThrow(new IllegalStateException("redis down"));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertThat(interceptor.beforeHandshake(request("ticket=tk", null), response,
                mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void afterHandshakeShouldDoNothing() {
        interceptor.afterHandshake(request("ticket=tk", null), mock(ServerHttpResponse.class),
                mock(org.springframework.web.socket.WebSocketHandler.class), new BizException("x"));
    }
}
