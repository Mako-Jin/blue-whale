package com.bluewhale.sts.audit.auditlog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计记录出参。
 *
 * <p>字段与实体一一对应，不做裁剪：审计的每一条都可能被查阅到细节
 * （"这次改的到底是哪一条""当时是从哪个 IP 来的""为什么被拒"），
 * 在列表接口上省掉字段，只会让使用者不得不再发一次详情请求。
 */
@Getter
@Setter
@Schema(name = "AuditLogVO", description = "审计记录")
public class AuditLogVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "动作标识，形如 user.create；永久契约，改名即历史数据断裂")
    private String action;

    @Schema(description = "被操作资源标识，形如 auth:user")
    private String resource;

    @Schema(description = "操作对象的主键或业务编码")
    private String targetId;

    @Schema(description = "操作人用户主键")
    private Long operatorId;

    @Schema(description = "操作人登录名")
    private String operatorName;

    @Schema(description = "操作人所属租户主键")
    private Long tenantId;

    @Schema(description = "结果：SUCCESS / FAILURE")
    private String result;

    @Schema(description = "失败时的错误码，成功为 null")
    private Integer errorCode;

    @Schema(description = "失败原因简述，成功为 null")
    private String errorMessage;

    @Schema(description = "HTTP 方法")
    private String httpMethod;

    @Schema(description = "请求路径（不含查询串）")
    private String requestUri;

    @Schema(description = "来源 IP")
    private String ip;

    @Schema(description = "User-Agent")
    private String userAgent;

    @Schema(description = "链路标识，取自请求头 X-Trace-Id")
    private String traceId;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "服务端记录时间（毫秒）")
    private Long serverTime;

}
