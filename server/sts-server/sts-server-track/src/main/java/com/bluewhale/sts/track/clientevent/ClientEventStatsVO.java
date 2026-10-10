package com.bluewhale.sts.track.clientevent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * 事件聚合结果。
 *
 * <p>把实际生效的时间窗一并回传：调用方没传时间时服务端会补默认值（7 天），
 * 若不回传"到底查的是哪一段"，用户看到总数偏小时会怀疑是丢了数据，
 * 而不是意识到自己查的是最近 7 天。
 */
@Getter
@Schema(name = "ClientEventStatsVO", description = "客户端行为事件聚合结果")
public class ClientEventStatsVO {

    @Schema(description = "时间窗内总条数")
    private final long total;

    @Schema(description = "实际生效的起始时间（毫秒）")
    private final long startTime;

    @Schema(description = "实际生效的结束时间（毫秒）")
    private final long endTime;

    /**
     * 按事件名分布。
     *
     * <p>与日志域不同，这里**不补齐"零条数的事件名"**：日志级别是封闭枚举，补零后可直接绘图；
     * 事件名是客户端可自由新增的开放集合，不存在可枚举的全集，凭空补一份清单只会误导读者。
     */
    @Schema(description = "按事件名分布，只包含时间窗内出现过的名称")
    private final List<ClientEventNameCount> names;

    public ClientEventStatsVO(long total, long startTime, long endTime, List<ClientEventNameCount> names) {
        this.total = total;
        this.startTime = startTime;
        this.endTime = endTime;
        this.names = names;
    }

}
