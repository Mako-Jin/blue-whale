package com.bluewhale.iot.service;

import com.bluewhale.iot.config.IotProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 设备实时推送单测：订阅计数、按设备投递与连接摘除。
 *
 * <p>摘除逻辑由 MVC 的完成/超时/异常回调驱动，脱离 MVC 时回调不会触发，
 * 故直接调用包内可见的 {@link IotStreamService#removeConnection} 验证。</p>
 *
 * @author BlueWhale
 */
class IotStreamServiceTest {

    private static final Long DEVICE_ID = 9L;

    private IotStreamService service;

    @BeforeEach
    void setUp() {
        IotProperties properties = new IotProperties();
        properties.setStreamHeartbeatSeconds(30);
        service = new IotStreamService(properties);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void subscribeRegistersAndRemovalDropsConnection() {
        SseEmitter emitter = service.subscribe(DEVICE_ID);

        assertEquals(1, service.activeConnections());

        service.removeConnection(DEVICE_ID, emitter);

        assertEquals(0, service.activeConnections());
        assertEquals(0, service.publish(DEVICE_ID, IotStreamService.EVENT_TELEMETRY, Map.of("value", 1)));
    }

    @Test
    void removeConnectionIsIdempotent() {
        SseEmitter emitter = service.subscribe(DEVICE_ID);

        service.removeConnection(DEVICE_ID, emitter);
        service.removeConnection(DEVICE_ID, emitter);

        assertEquals(0, service.activeConnections());
    }

    @Test
    void publishReachesOnlySubscribersOfThatDevice() {
        service.subscribe(DEVICE_ID);
        service.subscribe(DEVICE_ID);
        service.subscribe(10L);

        int delivered = service.publish(DEVICE_ID, IotStreamService.EVENT_TELEMETRY, Map.of("value", 1));

        assertEquals(2, delivered);
        assertEquals(0, service.publish(11L, IotStreamService.EVENT_TELEMETRY, Map.of("value", 1)));
        assertEquals(3, service.activeConnections());
    }

    @Test
    void heartbeatDoesNotBreakActiveConnections() {
        service.subscribe(DEVICE_ID);

        service.heartbeat();

        assertEquals(1, service.activeConnections());
    }
}
