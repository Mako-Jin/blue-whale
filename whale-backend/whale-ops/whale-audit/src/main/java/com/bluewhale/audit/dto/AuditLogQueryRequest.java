package com.bluewhale.audit.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 审计日志查询条件（分页 + 多维过滤）。
 * <p>时间范围走 {@code idx_create_time}；{@code traceId} 走 {@code idx_trace_id}，
 * 避免无条件全表扫描。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 操作用户ID */
    private Long userId;

    /** 操作用户名（模糊匹配） */
    private String username;

    /** 业务模块，如 whale-service-auth */
    private String module;

    /** 操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER */
    private String operateType;

    /** 业务结果：1 成功 / 0 失败 */
    private Integer bizStatus;

    /** 链路追踪ID */
    private String traceId;

    /** 起始时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
