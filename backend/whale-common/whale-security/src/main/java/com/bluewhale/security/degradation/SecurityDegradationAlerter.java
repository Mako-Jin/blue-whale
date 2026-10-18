package com.bluewhale.security.degradation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全降级统一告警器（AUTH-05，REQ-SEC-002 / REQ-SEC-003）。
 * <p>当安全组件（如令牌黑名单存储）不可用而被迫降级放行时，调用方通过本类输出
 * ERROR 级告警并按固定标记 {@value #ALARM_MARKER} 统一检索口径；同时自增进程内计数，
 * 供测试与运维读取降级次数。本类为纯静态工具，不作为 Spring Bean，以免在
 * Servlet（whale-auth）与 WebFlux（whale-gateway）两侧引入装配差异，也不改动任何
 * 既有构造器签名。</p>
 * <p>偏差登记：当前未接入 Micrometer/Prometheus 指标，可观测性以「ERROR 日志标记 +
 * 进程内计数」替代，属已登记偏差，待监控栈就绪后回填指标。</p>
 *
 * @author BlueWhale
 */
public final class SecurityDegradationAlerter {

    /** 固定告警标记：用于日志按标记检索，同时作为 logger 名称 */
    public static final String ALARM_MARKER = "SECURITY_DEGRADATION";

    /** 告警日志 logger：以固定标记命名，便于日志平台按 logger 名过滤 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ALARM_MARKER);

    /** 降级告警日志模板：含标记、场景标识与说明 */
    private static final String ALARM_MESSAGE = "[{}] 安全降级告警 scene={} detail={}";

    /** 进程内降级告警累计计数 */
    private static final AtomicLong ALARM_COUNT = new AtomicLong();

    /** 计数复位基准值 */
    private static final long RESET_BASE = 0L;

    /**
     * 私有构造器，禁止实例化
     */
    private SecurityDegradationAlerter() {
    }

    /**
     * 输出安全降级告警并自增进程内计数
     *
     * @param scene  降级场景标识，如 {@code auth:token-blacklist:contains}
     * @param detail 降级说明（描述降级后的替代语义）
     * @param cause  触发降级的异常，可为 {@code null}
     */
    public static void alarm(String scene, String detail, Throwable cause) {
        ALARM_COUNT.incrementAndGet();
        LOGGER.error(ALARM_MESSAGE, ALARM_MARKER, scene, detail, cause);
    }

    /**
     * 读取进程内累计降级告警次数
     *
     * @return 自上次复位以来的累计告警次数
     */
    public static long alarmCount() {
        return ALARM_COUNT.get();
    }

    /**
     * 复位进程内降级告警计数（供测试使用）
     */
    public static void resetAlarmCount() {
        ALARM_COUNT.set(RESET_BASE);
    }
}
