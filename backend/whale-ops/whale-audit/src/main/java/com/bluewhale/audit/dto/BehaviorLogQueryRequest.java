package com.bluewhale.audit.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 前端用户行为埋点查询条件（分页 + 多维过滤，REQ-SEC-006）。
 *
 * <p>时间范围走 {@code idx_create_time}；{@code traceId} / {@code eventType} 分别走
 * {@code idx_trace_id} / {@code idx_event_type}，避免无条件全表扫描。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BehaviorLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 链路追踪ID */
    private String traceId;

    /** 上报用户ID */
    private Long userId;

    /** 前端客户端标识（一次会话唯一） */
    private String clientId;

    /** 事件类型：PAGE_VIEW/CLICK/SUBMIT/CUSTOM */
    private String eventType;

    /** 事件名称（精确匹配） */
    private String eventName;

    /** 起始时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
