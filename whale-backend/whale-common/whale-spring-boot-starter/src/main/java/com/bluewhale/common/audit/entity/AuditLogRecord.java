package com.bluewhale.common.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志实体，对应 {@code audit_log} 表。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：审计日志为 append-only 数据（写入后不允许修改），
 * 表中既无 {@code update_time} 也无 {@code deleted}（见表注释），继承基类会引入不存在的列。</p>
 * <p>{@code id} 由 MyBatis-Plus 雪花算法（{@code IdType.ASSIGN_ID}）生成；
 * {@code createTime} 即操作发生时间，由切面显式写入。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("audit_log")
public class AuditLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 链路追踪ID（跨服务调用串联） */
    private String traceId;

    /** 操作用户ID（匿名访问为 NULL） */
    private Long userId;

    /** 租户ID（多租户扩展预留） */
    private Long tenantId;

    /** 操作用户名（冗余存储，用户删除后仍可追溯） */
    private String username;

    /** 业务模块，如 whale-service-auth */
    private String module;

    /** 操作描述，如 创建用户 */
    private String operation;

    /** 操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER */
    private String operateType;

    /** 请求方法（全限定类名#方法名） */
    private String method;

    /** 请求URI */
    private String requestUri;

    /** HTTP 方法：GET/POST/PUT/DELETE */
    private String httpMethod;

    /** 操作对象类型，如 sys_user / sys_role */
    private String targetType;

    /** 操作对象ID（批量操作为逗号分隔或 JSON 数组） */
    private String targetId;

    /** 请求参数（JSON 格式，敏感字段已脱敏） */
    private String requestParams;

    /** 返回体（JSON 格式，敏感字段已脱敏并截断；仅 {@code recordResult=true} 时写入） */
    private String resultBody;

    /** 响应状态码 */
    private Integer responseCode;

    /** 业务结果：1 成功 / 0 失败 */
    private Integer bizStatus;

    /** 失败原因（异常类名 + 消息） */
    private String errorMsg;

    /** 操作IP地址（兼容 IPv6） */
    private String ip;

    /** 用户代理（浏览器/客户端标识） */
    private String userAgent;

    /** 接口耗时（毫秒） */
    private Long costMs;

    /** 创建时间（即操作发生时间） */
    private LocalDateTime createTime;
}
