package com.bluewhale.audit.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 前端开发者日志查询条件（分页 + 多维过滤，REQ-SEC-006）。
 *
 * <p>时间范围走 {@code idx_create_time}；{@code traceId} / {@code clientId} 分别走
 * {@code idx_trace_id} / {@code idx_client_id}，避免无条件全表扫描。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FrontendLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 链路追踪ID */
    private String traceId;

    /** 上报用户ID */
    private Long userId;

    /** 前端客户端标识（一次会话唯一） */
    private String clientId;

    /** 日志级别：DEBUG/INFO/WARN/ERROR */
    private String logLevel;

    /** 起始时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
