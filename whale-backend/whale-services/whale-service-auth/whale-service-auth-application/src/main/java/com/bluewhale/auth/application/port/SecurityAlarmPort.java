package com.bluewhale.auth.application.port;

/**
 * 安全事件告警出站端口（AUTH-05，REQ-SEC-002 / REQ-SEC-003）。
 *
 * <p>应用层在安全事件（如 refreshToken 复用检测命中）发生时通过本端口上报告警，
 * 不直接依赖 whale-security 的静态告警工具——具体告警通道（日志标记、指标回填）
 * 由基础设施层适配实现，保持应用层框架与安全实现无关。</p>
 *
 * @author BlueWhale
 */
public interface SecurityAlarmPort {

    /**
     * 上报一条安全告警
     *
     * @param scene  场景标识，如 {@code auth:refresh-token:reuse}
     * @param detail 告警说明（描述事件与已采取的动作）
     * @param cause  触发告警的异常，可为 {@code null}
     */
    void alarm(String scene, String detail, Throwable cause);
}
