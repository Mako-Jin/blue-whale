package com.bluewhale.sts.audit.auditlog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计记录实体，对应表 {@code bw_audit_log}。
 *
 * <p><b>不继承任何基类，而且是三条管道里唯一"连逻辑删除都没有"的一张表。</b>
 * 本仓的 {@code BaseEntity} 提供五个审计列（{@code create_by/create_time/update_by/update_time/deleted}），
 * 但审计记录用的是另一套语义：
 * <ul>
 *   <li>{@code create_*} 与 {@code update_*} —— 审计记录**写完就不再变**，写操作人与写操作时间
 *       就是记录本身的 {@code operator_*} 与 {@code server_time}，再存一份是同一事实的两个副本；</li>
 *   <li>{@code deleted} —— 审计**不可删**。给它加逻辑删除，等于给"抹掉一条审计"留了一扇门，
 *       而这扇门一旦存在，"审计表里的记录是完整的"这个前提就不成立了。</li>
 * </ul>
 * 日志域与事件域在这一点上做了同样的取舍（见 {@code ClientLogEntity} /
 * {@code ClientEventEntity} 的注释），审计比它们更严格：那两张表是"可丢弃"的，
 * 这张表是"宁可留着"的。
 *
 * <p><b>只有服务端时间，没有客户端时间 —— 少一列不是遗漏。</b>
 * 另两条管道的双时间戳是为了提防"客户端时钟不可信"，而审计记录**根本不由客户端产生**：
 * 它由后端拦截器在请求结束时写下，时间戳只有一个来源。
 *
 * <p><b>属性名与列名同构，刻意不加 {@code @TableField} 指定列名。</b>
 * 见 {@code docs/decisions/0009} 第 3 条：MyBatis-Plus 一旦发现"列名与属性名不一致"，
 * 会自动补 {@code 列名 AS 属性名} 的别名，而别名可能又撞上保留字。
 * 让 {@code targetId → target_id} 走全局的下划线转驼峰推导，不产生别名。
 */
@Getter
@Setter
@TableName("bw_audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 动作标识，形如 {@code user.create}。由 {@code @Audited} 声明，是永久契约 */
    private String action;

    /** 被操作资源标识，形如 {@code auth:user}。约定与权限码同形 */
    private String resource;

    /** 操作对象的主键或业务编码。由业务代码通过 {@code AuditContext.target(...)} 补充 */
    private String targetId;

    /** 操作人用户主键。**只有已认证的请求才会产生审计**，因此这里不为空 */
    private Long operatorId;

    /** 操作人登录名。取自令牌，不经库查询 —— 见 {@code AuditInterceptor} 的说明 */
    private String operatorName;

    /** 操作人所属租户主键 */
    private Long tenantId;

    /** 结果：{@code SUCCESS} / {@code FAILURE}，取值见 {@link AuditResult} */
    private String result;

    /** 失败时的错误码，成功为 null */
    private Integer errorCode;

    /** 失败原因的简短描述，成功为 null */
    private String errorMessage;

    /** HTTP 方法，用于排查"这个动作当时是怎么被调用的" */
    private String httpMethod;

    /** 请求路径（不含查询串） */
    private String requestUri;

    private String ip;

    private String userAgent;

    /** 链路标识，取自请求头 {@code X-Trace-Id}，与前端日志串联用；未携带时为 null */
    private String traceId;

    /** 从进入拦截器到请求结束的耗时（毫秒） */
    private Long durationMs;

    /** 服务端记录时间（毫秒）。排序/分页/时间窗的唯一依据 */
    private Long serverTime;

}
