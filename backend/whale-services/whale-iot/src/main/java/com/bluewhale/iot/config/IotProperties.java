package com.bluewhale.iot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IoT 平台配置项（{@code whale.iot.*}）。
 *
 * <p>与 M1 的散装 {@code @Value} 不同，M2 起统一收敛到本类：EMQX 管理面凭据、
 * 遥测批次上限、SSE 心跳与指令默认超时。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.iot")
public class IotProperties {

    /** EMQX 连接配置 */
    private final Emqx emqx = new Emqx();

    /** 单设备单次上报的遥测条目上限（防超大批次拖垮落库） */
    private int telemetryMaxBatch = 100;

    /** 遥测查询单次返回条数上限 */
    private int telemetryQueryMax = 500;

    /** SSE 心跳间隔（秒）；连接空闲时按此间隔发送注释帧防中间代理掐链 */
    private int streamHeartbeatSeconds = 30;

    /** 指令默认超时（秒），仅用于展示与后续超时扫描 */
    private int commandTimeoutSeconds = 30;

    /** 规则引擎总开关（关闭后遥测链路不再评估规则） */
    private boolean ruleEnabled = true;

    /** 规则默认静默期（秒）：规则动作配置未指定时按此值去重 */
    private int ruleSilenceSeconds = 300;

    /** 告警动作对接配置（whale-alert 内网接口） */
    private final Alert alert = new Alert();

    /**
     * EMQX 管理面配置。
     *
     * <p>指令下发走 EMQX REST API {@code POST /api/v5/publish}（本服务不建常驻 MQTT 客户端），
     * 凭据为 Dashboard 生成的 API Key/Secret（Basic 认证）。未配置时下发链路降级为
     * 「只落库 pending、不投递」，不影响接入与遥测。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Emqx {

        /** EMQX 管理面基地址（REST API 端口 18083） */
        private String apiBaseUrl = "http://127.0.0.1:18083";

        /** API Key（Dashboard → API Keys 生成） */
        private String apiKey = "";

        /** API Secret */
        private String apiSecret = "";

        /** Broker 地址（设备侧连接用，仅展示/文档用途） */
        private String mqttHost = "127.0.0.1";

        /** Broker MQTT 端口 */
        private int mqttPort = 1883;

        /** 发布超时（秒） */
        private int publishTimeoutSeconds = 5;
    }

    /**
     * 告警动作配置。
     *
     * <p>规则命中后调用 whale-alert 内网接口 {@code POST /alert/internal/record}
     * 落一条告警记录（告警的渠道分发由 whale-alert 负责）。调用失败只记日志、
     * 不回滚遥测，也不影响其它规则。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Alert {

        /** whale-alert 基地址（internal 路径由本服务拼接） */
        private String baseUrl = "http://127.0.0.1:9103";

        /** 调用超时（秒） */
        private int timeoutSeconds = 3;
    }
}
