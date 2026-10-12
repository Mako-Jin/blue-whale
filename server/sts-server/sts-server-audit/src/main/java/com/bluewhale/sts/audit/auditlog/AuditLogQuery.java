package com.bluewhale.sts.audit.auditlog;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计查询条件。
 *
 * <p>过滤维度刻意与"审计最常被问的问题"一一对应：
 * "这条数据被谁动过"（{@code targetId}）、"这个人都做过什么"（{@code operatorId}）、
 * "哪些操作失败了"（{@code result}）、"涉及用户权限的操作"（{@code resource}）。
 * 多加一个没人会用的过滤条件，只会让索引设计与界面都变重。
 *
 * <p>时间以**毫秒时间戳**而非日期字符串传参，且过滤的是 {@code serverTime} ——
 * 审计只有一个时间来源，不存在"用哪个时间排序"的歧义（与另两条管道的双时间戳不同）。
 */
@Getter
@Setter
@Schema(name = "AuditLogQuery", description = "审计查询条件")
public class AuditLogQuery extends PageQuery {

    @Schema(description = "动作标识精确匹配，如 user.create")
    private String action;

    @Schema(description = "资源标识精确匹配，如 auth:user")
    private String resource;

    @Schema(description = "操作对象精确匹配")
    private String targetId;

    @Schema(description = "操作人用户主键精确匹配", type = "integer", format = "int64")
    private Long operatorId;

    @Schema(description = "操作人登录名精确匹配")
    private String operatorName;

    @Schema(description = "结果精确匹配，取值 SUCCESS / FAILURE")
    private String result;

    /*
     * 时间字段显式写出 type/format，不能只写 description。
     *
     * springdoc 在**查询参数**位置遇到带 @Schema 的字段时，会以注解为准重建 schema；
     * 而注解的 type 默认为空串，于是字段类型被"推平"成 string —— 契约里变成
     * `{"type":"string"}`，前端据此生成的参数类型也就成了字符串，实际传数字却被 TS 拦住。
     * 响应体里的同类型字段不受影响（走的是另一条解析路径），所以这个问题只在查询参数上出现。
     */
    @Schema(description = "起始时间（毫秒，含）；缺省为 endTime 之前 7 天", type = "integer", format = "int64")
    private Long startTime;

    @Schema(description = "结束时间（毫秒，含）；缺省为当前时间", type = "integer", format = "int64")
    private Long endTime;

}
