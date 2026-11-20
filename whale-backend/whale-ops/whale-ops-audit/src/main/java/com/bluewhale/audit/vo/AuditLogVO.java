package com.bluewhale.audit.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志视图对象（只读，字段与 {@code audit_log} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class AuditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 操作用户ID */
    private Long userId;

    /** 租户ID */
    private Long tenantId;

    /** 操作用户名 */
    private String username;

    /** 业务模块 */
    private String module;

    /** 操作描述 */
    private String operation;

    /** 操作类型 */
    private String operateType;

    /** 请求方法（全限定类名#方法名） */
    private String method;

    /** 请求URI */
    private String requestUri;

    /** HTTP 方法 */
    private String httpMethod;

    /** 操作对象类型 */
    private String targetType;

    /** 操作对象ID */
    private String targetId;

    /** 请求参数（JSON，敏感字段已脱敏） */
    private String requestParams;

    /** 返回体（JSON，敏感字段已脱敏并截断；仅 recordResult=true 时非空） */
    private String resultBody;

    /** 响应状态码 */
    private Integer responseCode;

    /** 业务结果：1 成功 / 0 失败 */
    private Integer bizStatus;

    /** 失败原因 */
    private String errorMsg;

    /** 操作IP地址 */
    private String ip;

    /** 用户代理 */
    private String userAgent;

    /** 接口耗时（毫秒） */
    private Long costMs;

    /** 创建时间（即操作发生时间） */
    private LocalDateTime createTime;
}
