package com.bluewhale.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后端接口日志实体，对应 {@code api_log} 表（REQ-SEC-006）。
 *
 * <p>由 {@code ApiLogFilter} 采集每个 HTTP 请求的入参/出参/耗时/结果，异步落库。</p>
 * <p><b>不继承 {@code BaseEntity}</b>：接口日志为 append-only 数据（写入后不允许修改），
 * 表中既无 {@code update_time} 也无 {@code deleted}；{@code id} 由雪花算法生成，
 * {@code createTime} 即请求处理时刻。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("api_log")
public class ApiLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 链路追踪ID（跨服务调用串联） */
    private String traceId;

    /** 操作用户ID（匿名访问为 NULL） */
    private Long userId;

    /** 操作用户名（冗余存储，用户删除后仍可追溯） */
    private String username;

    /** 租户ID（网关可信注入，缺失为 NULL） */
    private Long tenantId;

    /** 业务模块，如 whale-auth */
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
