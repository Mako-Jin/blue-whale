package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocCollabStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocCollabRedisListener} 跨实例帧过滤与分发断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocCollabRedisListenerTest {

    @Mock
    private DocCollabSessionRegistry registry;

    @Mock
    private DocCollabStore collabStore;

    private final ObjectMapper mapper = new ObjectMapper();

    private Message message(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private DocCollabRedisListener listener() {
        when(collabStore.instanceId()).thenReturn("self");
        return new DocCollabRedisListener(mapper, registry, collabStore);
    }

    @Test
    void shouldIgnoreOwnOrigin() {
        DocCollabRedisListener listener = listener();

        listener.onMessage(message("{\"origin\":\"self\",\"tenantId\":3,\"pageId\":9,\"frame\":\"{}\"}"), null);

        verify(registry, never()).broadcastLocal(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldBroadcastFrameFromOtherInstance() {
        DocCollabRedisListener listener = listener();

        listener.onMessage(message("{\"origin\":\"other\",\"tenantId\":3,\"pageId\":9,\"frame\":\"{\\\"type\\\":\\\"pong\\\"}\","
                + "\"excludeClientId\":\"c1\",\"targetClientId\":\"c2\"}"), null);

        verify(registry).broadcastLocal(3L, 9L, "{\"type\":\"pong\"}", "c1", "c2");
    }

    @Test
    void shouldIgnoreEnvelopeWithoutFrame() {
        DocCollabRedisListener listener = listener();

        listener.onMessage(message("{\"origin\":\"other\",\"tenantId\":3,\"pageId\":9}"), null);

        verify(registry, never()).broadcastLocal(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldTreatNullFrameAsMissing() {
        DocCollabRedisListener listener = listener();

        listener.onMessage(message("{\"origin\":\"other\",\"tenantId\":3,\"pageId\":9,\"frame\":null}"), null);

        verify(registry, never()).broadcastLocal(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSwallowMalformedMessage() {
        DocCollabRedisListener listener = listener();

        listener.onMessage(message("not-json"), null);

        verify(registry, never()).broadcastLocal(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPassNullFiltersThroughAndDefaultIds() {
        DocCollabRedisListener listener = listener();

        listener.onMessage(message("{\"origin\":\"other\",\"frame\":\"{}\"}"), null);

        verify(registry).broadcastLocal(0L, 0L, "{}", null, null);
    }
}
