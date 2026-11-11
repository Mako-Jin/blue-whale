package com.bluewhale.twin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** internal_api 绑定源运行时配置 */
    private final InternalApi internalApi = new InternalApi();

    /** 绑定轮询运行时配置 */
    private final Runtime runtime = new Runtime();

    /** AI 解读（whale-assistant 开放 API）配置 */
    private final Assistant assistant = new Assistant();

    /** 告警外发配置（Wave 6.10，默认关闭） */
    private final AlertNotify alertNotify = new AlertNotify();

    /**
     * AI 解读配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Assistant {

        /** whale-assistant 基础地址（内网直连，不经网关） */
        private String baseUrl = "http://127.0.0.1:8087";

        /** 目标助手编码（须为已发布助手；为空表示未启用 AI 解读） */
        private String assistantCode = "";

        /** 单次问答超时（秒） */
        private int timeoutSeconds = 60;
    }

    /**
     * internal_api 绑定源配置：运行时按 bindKey 轮询平台内部只读接口。
     *
     * <p>bindKey 形如 {@code /iot/internal/device/state?deviceId=1}——首段是服务名，
     * 用于解析 base-url；整条路径必须命中 {@code allowedPrefixes} 白名单才可调用，
     * 且只发 GET（写接口不在白名单口径内）。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class InternalApi {

        /** 是否启用 internal_api 轮询源 */
        private boolean enabled = true;

        /** 单次调用超时（秒） */
        private int timeoutSeconds = 5;

        /**
         * 可调用路径前缀白名单：bindKey 必须以其中一项开头；
         * 约束为各服务 {@code /internal/**} 只读端点与 assistant 开放只读接口。
         */
        private List<String> allowedPrefixes = List.of(
                "/iot/internal/", "/twin/internal/", "/assistant/openapi/");

        /**
         * 服务名 → base-url 路由表（内网直连，不经网关）；
         * 未配置的服务名回退：iot 走 {@code whale.twin.iot.base-url}，其余拒绝。
         */
        private Map<String, String> baseUrls = new LinkedHashMap<>();
    }

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

        /** 单场景单次 tick 允许触达的最大 internal_api 绑定数（防误配拖垮内部服务） */
        private int maxApisPerScene = 20;

        /**
         * 无头评估开关（Wave 6.08）：开启后无订阅者的场景也会周期轮询并落库告警，
         * 关闭则回退到「只有人看才评估」的 6.07 行为。
         */
        private boolean headlessEnabled = true;

        /** 无头评估轮次间隔（毫秒，独立于 tick，默认 15s） */
        private long headlessIntervalMs = 15000;

        /** 单轮无头评估覆盖的最大场景数（按场景ID升序截断，防大租户拖垮） */
        private int maxHeadlessScenes = 20;
    }

    /**
     * 告警外发配置（Wave 6.10）：级别变化后把告警推到平台外部。
     *
     * <p>默认关闭——没有配置 webhook 地址时不应产生任何出站请求。
     * webhook 地址属运维配置（不是用户输入），但仍做形态校验，避免把内网令牌或
     * 带凭据的 URL 发到意外目标。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class AlertNotify {

        /** 外发总开关（默认关：避免空配置下无意义出站） */
        private boolean enabled = false;

        /** 最低外发级别：warn（默认，含 critical）/ critical（仅严重） */
        private String minLevel = "warn";

        /** 单地址 webhook 超时（秒，连接与读取共用） */
        private int timeoutSeconds = 5;

        /** webhook 地址列表（http/https；非法地址跳过，不影响其它地址） */
        private List<String> webhookUrls = new ArrayList<>();

        /** 可选 Bearer 令牌（空表示不加 Authorization 头） */
        private String webhookToken = "";

        /** 同场景同状态键同级别的最小外发间隔（秒），0 = 不去重 */
        private int cooldownSeconds = 300;

        /** 站内信通道开关（M1；走 whale-message 内网发送） */
        private boolean inappEnabled = false;

        /** whale-message 内网基础地址（站内信通道用，不经网关） */
        private String messageBaseUrl = "http://127.0.0.1:8094";

        /** 站内信单次最大接收人数（超限截断，与 whale-message 单次 100 人上限对齐） */
        private int maxRecipients = 20;

        /** 恢复通知开关（Wave 6.11 M0：级别回落到 normal 时补一条「已恢复」） */
        private boolean recoveryEnabled = true;

        /** 全局静默开始（HH:mm；与结束同为空表示不静默） */
        private String quietHoursStart = "";

        /** 全局静默结束（HH:mm；可跨零点，如 22:00–08:00） */
        private String quietHoursEnd = "";

        /** 静默期内是否允许严重告警穿透（默认允许：静默不该压掉真事故） */
        private boolean quietHoursCriticalBypass = true;

        /** whale-service-auth 内网地址（接收人检索走 /user/internal/directory） */
        private String authBaseUrl = "http://127.0.0.1:8081";

        /** 用户偏好过滤开关（Wave 6.17c；关闭或解析失败时按「全部放行」处理） */
        private boolean preferenceEnabled = true;
    }
}
