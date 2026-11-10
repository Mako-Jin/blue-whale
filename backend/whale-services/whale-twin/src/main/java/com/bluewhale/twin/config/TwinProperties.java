package com.bluewhale.twin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数字孪生平台配置属性（{@code whale.twin.*}）。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.twin")
public class TwinProperties {

    /** SSE 心跳间隔（秒），空闲连接按此发注释帧防代理掐链 */
    private int streamHeartbeatSeconds = 30;

    /** whale-iot 内网实时态来源配置 */
    private final Iot iot = new Iot();

    /** 绑定轮询运行时配置 */
    private final Runtime runtime = new Runtime();

    /**
     * whale-iot 内网接口配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Iot {

        /** whale-iot 基础地址（内网直连，不经网关） */
        private String baseUrl = "http://127.0.0.1:8088";

        /** 单次实时态查询超时（秒） */
        private int timeoutSeconds = 5;
    }

    /**
     * 绑定轮询运行时配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Runtime {

        /** 是否启用绑定轮询（关掉后仅保留绑定 CRUD 与手动快照查询） */
        private boolean enabled = true;

        /** 调度tick间隔（毫秒）；绑定实际轮询周期由其 pollIntervalMs 决定（不小于 tick） */
        private long tickMs = 5000;

        /** 单场景单次 tick 允许触达的最大设备数（防止误配拖垮 whale-iot） */
        private int maxDevicesPerScene = 50;
    }
}
