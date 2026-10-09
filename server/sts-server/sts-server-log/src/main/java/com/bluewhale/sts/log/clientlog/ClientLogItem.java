package com.bluewhale.sts.log.clientlog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 一条上报日志。
 *
 * <p>字段与前端 {@code common-tools} 的 {@code LogRecord} 一一对应 ——
 * 前端拿到 {@code onRecord} 钩子里的记录后，无需任何转换即可直接入队上报。
 * 这种"契约镜像"是刻意的：中间多一层 DTO 转换，就多一处会写错的字段名。
 */
@Getter
@Setter
@Schema(name = "ClientLogItem", description = "单条客户端日志")
public class ClientLogItem {

    @Schema(description = "级别，取值 trace/debug/perf/info/success/warn/error", example = "info")
    private String level;

    @Schema(description = "命名空间，形如 app › auth › login", example = "app › auth")
    private String namespace;

    @Schema(description = "客户端时间戳（毫秒），服务端仅作参考不用于排序")
    private Long timestamp;

    @Schema(description = "消息正文", example = "用户登录成功")
    private String message;

    @Schema(description = "随消息附带的原始数据")
    private List<Object> data;

    @Schema(description = "结构化上下文")
    private Map<String, Object> context;

    @Schema(description = "链路标识")
    private String traceId;

    @Schema(description = "会话标识")
    private String sessionId;

    @Schema(description = "触发日志时的路由")
    private String route;

    @Schema(description = "应用版本 / 构建号")
    private String release;

}
