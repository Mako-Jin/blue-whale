package com.bluewhale.audit.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 后端出站三方调用日志查询条件（分页 + 多维过滤，REQ-SEC-006）。
 *
 * <p>时间范围走 {@code idx_create_time}；{@code traceId} / {@code userId} / {@code bizStatus} 分别走
 * {@code idx_trace_id} / {@code idx_user_id} / {@code idx_biz_status}；{@code targetHost} 走
 * {@code idx_target_host}，避免无条件全表扫描。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutboundLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 链路追踪ID（出站请求头透传） */
    private String traceId;

    /** 发起调用的用户ID */
    private Long userId;

    /** 业务模块，如 whale-service-auth */
    private String module;

    /** 目标主机（host:port，精确匹配） */
    private String targetHost;

    /** HTTP 方法 */
    private String httpMethod;

    /** 业务结果：1 成功 / 0 失败 */
    private Integer bizStatus;

    /** 起始时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
