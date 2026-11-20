package com.bluewhale.audit.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后端接口日志视图对象（只读，字段与 {@code api_log} 表一致，REQ-SEC-006）
 *
 * @author BlueWhale
 */
@Data
public class ApiLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 操作用户ID（匿名访问为 NULL） */
    private Long userId;

    /** 操作用户名（冗余存储，用户删除后仍可追溯） */
    private String username;

    /** 业务模块，如 whale-service-auth */
    private String module;

    /** 请求URI */
    private String requestUri;

    /** HTTP 方法：GET/POST/PUT/DELETE */
    private String httpMethod;

    /** 查询串（敏感字段已脱敏） */
    private String queryString;

    /** 请求体（JSON，敏感字段已脱敏） */
    private String requestParams;

    /** 响应体（JSON，敏感字段已脱敏，超限截断） */
    private String responseBody;

    /** 响应状态码（HTTP 状态） */
    private Integer responseCode;

    /** 业务结果：1 成功 / 0 失败 */
    private Integer bizStatus;

    /** 失败原因（异常类名 + 消息） */
    private String errorMsg;

    /** 调用方IP（兼容 IPv6） */
    private String ip;

    /** 用户代理（浏览器/客户端标识） */
    private String userAgent;

    /** 接口耗时（毫秒） */
    private Long costMs;

    /** 创建时间（即请求处理时刻） */
    private LocalDateTime createTime;
}
