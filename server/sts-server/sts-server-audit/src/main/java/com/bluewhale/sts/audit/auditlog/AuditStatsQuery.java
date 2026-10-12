package com.bluewhale.sts.audit.auditlog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计聚合查询条件。
 *
 * <p>与 {@link AuditLogQuery} 分开而不是复用它：聚合只关心"哪段时间、哪个资源、哪个人"，
 * 复用完整查询对象会诱导调用方传一堆被静默忽略的参数 ——
 * 被静默忽略的入参，比不存在的入参更容易让人误判结果（与日志域同一取舍）。
 */
@Getter
@Setter
@Schema(name = "AuditStatsQuery", description = "审计聚合查询条件")
public class AuditStatsQuery {

    @Schema(description = "资源标识精确匹配，如 auth:user")
    private String resource;

    @Schema(description = "操作人用户主键精确匹配", type = "integer", format = "int64")
    private Long operatorId;

    /* 显式写出 type/format：原因见 AuditLogQuery 中同一位置的说明 */
    @Schema(description = "起始时间（毫秒，含）；缺省为 endTime 之前 7 天", type = "integer", format = "int64")
    private Long startTime;

    @Schema(description = "结束时间（毫秒，含）；缺省为当前时间", type = "integer", format = "int64")
    private Long endTime;

}
