package com.bluewhale.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 待办超时扫描与提醒配置（{@code bluewhale.workflow.timeout.*}）。
 *
 * <p>超时只做「扫描 + 提醒 + 指标」，<b>不自动推进流程</b>；提醒默认关闭，
 * 开启前必须保证 auth / message 内网地址可达，否则每次扫描只会产生告警日志。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "bluewhale.workflow.timeout")
public class WorkflowTimeoutProperties {

    /** 扫描 cron（默认每 10 分钟；也可由调度中心按 code 触发） */
    private String scanCron = "0 */10 * * * *";

    /** 单次扫描上限（防止一次拉全表） */
    private int scanLimit = 500;

    /** 是否发送站内信提醒（默认关闭，失败只告警） */
    private boolean notifyEnabled = false;

    /** whale-service-auth 内网基础地址（账号 → 用户ID 精确解析） */
    private String authBaseUrl = "http://127.0.0.1:8081";

    /** whale-message 内网基础地址（站内信经 /message/v1/internal/send 投递） */
    private String messageBaseUrl = "http://127.0.0.1:8094";

    /** 内网调用连接超时（毫秒）：提醒是附属能力，不能拖住调度线程 */
    private int connectTimeoutMillis = 800;

    /** 内网调用读超时（毫秒） */
    private int readTimeoutMillis = 1500;
}
