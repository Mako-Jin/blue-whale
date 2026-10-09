package com.bluewhale.sts.log.clientlog;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户端日志查询条件。
 *
 * <p>时间以**毫秒时间戳**而非日期字符串传参：前端 {@code LogRecord} 里的时间本来就是毫秒数，
 * 传字符串就得引入时区与格式约定（谁转、按谁的时区、要不要带秒），
 * 而这套约定在两端口径不一致时的表现是"数据查得到但少了几个小时"，极难排查。
 *
 * <p>过滤的是 {@code serverTime} 而非客户端时间：客户端时钟不可信（见建表脚本注释）。
 */
@Getter
@Setter
@Schema(name = "ClientLogQuery", description = "客户端日志查询条件")
public class ClientLogQuery extends PageQuery {

    @Schema(description = "上报方应用标识")
    private String app;

    @Schema(description = "级别精确匹配，取值 trace/debug/perf/info/success/warn/error")
    private String level;

    @Schema(description = "命名空间精确匹配")
    private String namespace;

    @Schema(description = "链路标识精确匹配")
    private String traceId;

    @Schema(description = "会话标识精确匹配")
    private String sessionId;

    @Schema(description = "消息正文模糊匹配")
    private String keyword;

    /*
     * 时间字段显式写出 type/format，不能只写 description。
     *
     * springdoc 在**查询参数**位置遇到带 @Schema 的字段时，会以注解为准重建 schema；
     * 而注解的 type 默认为空串，于是字段类型被"推平"成 string —— 契约里变成
     * `{"type":"string"}`，前端据此生成的参数类型也就成了字符串，
     * 实际传数字却被 TS 拦住。响应体里的同类型字段不受影响（走的是另一条解析路径），
     * 所以这个问题只在查询参数上出现，很容易被漏掉。
     */
    @Schema(description = "起始时间（毫秒，含）；缺省为 endTime 之前 7 天", type = "integer", format = "int64")
    private Long startTime;

    @Schema(description = "结束时间（毫秒，含）；缺省为当前时间", type = "integer", format = "int64")
    private Long endTime;

}
