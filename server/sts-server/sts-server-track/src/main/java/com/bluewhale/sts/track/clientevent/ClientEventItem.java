package com.bluewhale.sts.track.clientevent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 一条上报事件。
 *
 * <p>字段与前端 SDK 产出的事件记录一一对应：前端拿到 {@code track()} 的参数后，
 * 无需任何转换即可直接入队上报。这种"契约镜像"是刻意的 ——
 * 中间多一层 DTO 转换，就多一处会写错的字段名。
 */
@Getter
@Setter
@Schema(name = "ClientEventItem", description = "单条客户端行为事件")
public class ClientEventItem {

    @Schema(description = "客户端生成的事件唯一 id，用于幂等去重", example = "01J8Z0QW7X9K3M")
    private String eventId;

    @Schema(description = "事件名，小写点分、至少两段，形如 order.submit", example = "order.submit")
    private String eventName;

    @Schema(description = "登录用户标识，未登录时为空")
    private String userId;

    @Schema(description = "匿名设备标识")
    private String deviceId;

    @Schema(description = "会话标识")
    private String sessionId;

    @Schema(description = "触发事件时的路由")
    private String route;

    @Schema(description = "应用版本 / 构建号")
    private String release;

    @Schema(description = "事件属性")
    private Map<String, Object> props;

    @Schema(description = "属性结构版本，props 结构变化必须递增", example = "1")
    private Integer propsVersion;

    @Schema(description = "客户端时间戳（毫秒），服务端仅作参考不用于排序")
    private Long timestamp;

}
