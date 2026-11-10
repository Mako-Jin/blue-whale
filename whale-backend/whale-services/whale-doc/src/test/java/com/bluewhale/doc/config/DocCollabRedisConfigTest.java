package com.bluewhale.doc.config;

import com.bluewhale.doc.ws.DocCollabRedisListener;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link DocCollabRedisConfig} 监听容器装配断言。
 *
 * @author BlueWhale
 */
class DocCollabRedisConfigTest {

    @Test
    void shouldCreateContainerWithConnectionFactoryAndListener() {
        DocCollabRedisConfig config = new DocCollabRedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        DocCollabRedisListener listener = mock(DocCollabRedisListener.class);

        RedisMessageListenerContainer container = config.docCollabListenerContainer(factory, listener);

        assertThat(container).isNotNull();
        assertThat(container.getConnectionFactory()).isSameAs(factory);
        assertThat(container.isRunning()).isFalse();
    }
}
