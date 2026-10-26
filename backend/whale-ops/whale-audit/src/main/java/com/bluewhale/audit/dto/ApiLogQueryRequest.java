package com.bluewhale.audit.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 后端接口日志查询条件（分页 + 多维过滤，REQ-SEC-006）。
 *
 * <p>时间范围走 {@code idx_create_time}；{@code traceId} / {@code userId} / {@code bizStatus} 分别走
 * {@code idx_trace_id} / {@code idx_user_id} / {@code idx_biz_status}，避免无条件全表扫描。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 链路追踪ID */
    private String traceId;

    /** 操作用户ID（匿名访问为 NULL） */
    private Long userId;

    /** 业务模块，如 whale-service-auth */
    private String module;

    /** 请求URI（精确匹配） */
    private String requestUri;

    /** HTTP 方法：GET/POST/PUT/DELETE */
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
