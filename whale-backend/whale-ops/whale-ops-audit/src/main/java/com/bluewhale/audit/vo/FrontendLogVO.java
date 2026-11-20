package com.bluewhale.audit.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 前端开发者日志视图对象（只读，字段与 {@code frontend_log} 表一致，REQ-SEC-006）
 *
 * @author BlueWhale
 */
@Data
public class FrontendLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 上报用户ID（token 存在时由网关回填） */
    private Long userId;

    /** 前端客户端标识（一次会话唯一） */
    private String clientId;

    /** 页面URL（query 已掩码） */
    private String pageUrl;

    /** 日志级别：DEBUG/INFO/WARN/ERROR */
    private String logLevel;

    /** 日志内容（超限截断，敏感键掩码） */
    private String message;

    /** 错误堆栈（ERROR 时） */
    private String stack;

    /** 浏览器/UA 摘要 */
    private String browser;

    /** 创建时间（前端产生时间约为上报时间） */
    private LocalDateTime createTime;
}
