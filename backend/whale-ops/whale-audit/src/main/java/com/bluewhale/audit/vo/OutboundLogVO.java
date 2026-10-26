package com.bluewhale.audit.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后端出站三方调用日志视图对象（只读，字段与 {@code outbound_log} 表一致，REQ-SEC-006）
 *
 * @author BlueWhale
 */
@Data
public class OutboundLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 链路追踪ID（出站请求头透传） */
    private String traceId;

    /** 发起调用的用户ID */
    private Long userId;

    /** 发起调用的用户名 */
    private String username;

    /** 业务模块，如 whale-service-auth */
    private String module;

    /** 目标主机（host:port） */
    private String targetHost;

    /** 目标URI（含路径，query 已脱敏） */
    private String targetUri;

    /** HTTP 方法 */
    private String httpMethod;

    /** 出站请求体（JSON，敏感字段已脱敏） */
    private String requestParams;

    /** 出站响应体（JSON，敏感字段已脱敏，超限截断） */
    private String responseBody;

    /** 响应状态码（HTTP 状态） */
    private Integer responseCode;

    /** 业务结果：1 成功 / 0 失败（超时/连接异常为 0） */
    private Integer bizStatus;

    /** 失败原因（异常类名 + 消息） */
    private String errorMsg;

    /** 调用耗时（毫秒） */
    private Long costMs;

    /** 创建时间（即调用发生时刻） */
    private LocalDateTime createTime;
}
