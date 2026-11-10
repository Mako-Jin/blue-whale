package com.bluewhale.doc.config;

import com.bluewhale.doc.ws.DocCollabRedisListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 协作跨实例广播订阅配置（M2）。
 *
 * <p>仅在 {@code whale.doc.collab-enabled=true} 时创建监听容器，避免协作未启用时
 * 启动阶段连接 Redis；容器订阅 {@code whale:doc:collab:channel:*} 并交给
 * {@link DocCollabRedisListener} 分发。</p>
 *
 * @author BlueWhale
 */
@Configuration
@ConditionalOnProperty(prefix = "whale.doc", name = "collab-enabled", havingValue = "true")
public class DocCollabRedisConfig {

    /** 协作广播频道模式 */
    private static final String CHANNEL_PATTERN = "whale:doc:collab:channel:*";

    /**
     * 创建协作广播监听容器。
     *
     * @param factory   Redis 连接工厂
     * @param listener  协作帧监听器
     * @return 监听容器
     */
    @Bean
    public RedisMessageListenerContainer docCollabListenerContainer(RedisConnectionFactory factory,
                                                                  DocCollabRedisListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new PatternTopic(CHANNEL_PATTERN));
        return container;
    }
}
