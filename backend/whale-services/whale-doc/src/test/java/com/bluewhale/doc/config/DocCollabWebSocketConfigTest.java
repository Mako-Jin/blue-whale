package com.bluewhale.doc.config;

import com.bluewhale.doc.ws.DocCollabWebSocketHandler;
import com.bluewhale.doc.ws.DocWsHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocCollabWebSocketConfig} 容器参数与端点注册断言。
 *
 * @author BlueWhale
 */
class DocCollabWebSocketConfigTest {

    private final DocCollabWebSocketHandler handler = mock(DocCollabWebSocketHandler.class);

    private final DocWsHandshakeInterceptor interceptor = mock(DocWsHandshakeInterceptor.class);

    private final DocProperties properties = new DocProperties();

    @Test
    void containerShouldAlignBufferSizesAndIdleTimeout() {
        properties.setCollabMaxUpdateBytes(4096L);
        DocCollabWebSocketConfig config = new DocCollabWebSocketConfig(handler, interceptor, properties);

        ServletServerContainerFactoryBean container = config.docWebSocketContainer();

        assertThat(container.getMaxTextMessageBufferSize()).isEqualTo(4096);
        assertThat(container.getMaxBinaryMessageBufferSize()).isEqualTo(4096);
        assertThat(container.getMaxSessionIdleTimeout()).isEqualTo(5 * 60 * 1000L);
    }

    @Test
    void containerShouldCapBufferAtIntegerMax() {
        properties.setCollabMaxUpdateBytes(Long.MAX_VALUE);
        DocCollabWebSocketConfig config = new DocCollabWebSocketConfig(handler, interceptor, properties);

        assertThat(config.docWebSocketContainer().getMaxTextMessageBufferSize()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void shouldRegisterEndpointWithInterceptorAndOrigin() {
        DocCollabWebSocketConfig config = new DocCollabWebSocketConfig(handler, interceptor, properties);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/doc/ws")).thenReturn(registration);
        when(registration.addInterceptors(interceptor)).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(handler, "/doc/ws");
        verify(registration).addInterceptors(interceptor);
        verify(registration).setAllowedOrigins("*");
    }
}
