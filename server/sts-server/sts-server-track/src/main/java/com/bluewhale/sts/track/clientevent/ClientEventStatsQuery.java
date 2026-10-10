package com.bluewhale.sts.track.clientevent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 事件聚合查询条件。与 {@link ClientEventQuery} 分开，是因为聚合只关心"哪段时间、哪个应用"，
 * 复用完整查询对象会诱导调用方传一堆被忽略的参数 —— 被静默忽略的入参，
 * 比不存在的入参更容易让人误判结果。
 */
@Getter
@Setter
@Schema(name = "ClientEventStatsQuery", description = "客户端行为事件聚合查询条件")
public class ClientEventStatsQuery {

    @Schema(description = "上报方应用标识")
    private String app;

    /* 显式写出 type/format：原因见 ClientEventQuery 中同一位置的说明 */
    @Schema(description = "起始时间（毫秒，含）；缺省为 endTime 之前 7 天", type = "integer", format = "int64")
    private Long startTime;

    @Schema(description = "结束时间（毫秒，含）；缺省为当前时间", type = "integer", format = "int64")
    private Long endTime;

}
