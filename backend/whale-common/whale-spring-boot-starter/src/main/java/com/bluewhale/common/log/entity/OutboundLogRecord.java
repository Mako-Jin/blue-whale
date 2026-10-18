package com.bluewhale.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后端出站三方调用日志实体，对应 {@code outbound_log} 表（REQ-SEC-006）。
 *
 * <p>由 {@code OutboundLogInterceptor} 采集出站 HTTP 调用（经 {@code RestTemplateBuilder} 构建的
 * {@code RestTemplate}）的 method/uri/host/耗时/结果，异步落库。</p>
 * <p><b>不继承 {@code BaseEntity}</b>：append-only（无 {@code update_time} / {@code deleted}）；
 * {@code id} 雪花、{@code createTime} 即调用发生时刻。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("outbound_log")
public class OutboundLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 链路追踪ID（出站请求头透传） */
    private String traceId;

    /** 发起调用的用户ID */
    private Long userId;

    /** 发起调用的用户名 */
    private String username;

    /** 业务模块，如 whale-auth */
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
