package com.bluewhale.audit.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 前端日志批量上报请求体（前端 console 与行为埋点共用，REQ-SEC-006）。
 *
 * <p>由前端 {@code whale-log-sdk} 经 {@code POST /audit/log/frontend/batch} 与
 * {@code POST /audit/log/behavior/batch} 上报；{@code items} 为共用条目列表，
 * 上限 50 条（见 {@code LogIngestController}）。</p>
 *
 * @author BlueWhale
 */
@Data
public class LogBatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 链路追踪ID（网关 {@code X-Trace-Id} 注入，前端透传） */
    private String traceId;

    /** 上报用户ID（浏览器可伪造，仅保留字段兼容旧版 SDK；服务端以 {@code X-User-Id} 头为准并忽略此值） */
    private Long userId;

    /** 前端客户端标识（一次会话唯一） */
    private String clientId;

    /** 页面URL（query 已掩码） */
    private String pageUrl;

    /** 日志条目列表（前端 console 或行为埋点，上限 50 条） */
    private List<LogItem> items;
}
