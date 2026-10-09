package com.bluewhale.sts.log.clientlog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * 日志聚合结果。
 *
 * <p>把实际生效的时间窗一并回传：调用方没传时间时服务端会补默认值（7 天），
 * 若不回传"到底查的是哪一段"，用户看到总数偏小时会怀疑是丢了数据，
 * 而不是意识到自己查的是最近 7 天。
 */
@Getter
@Schema(name = "ClientLogStatsVO", description = "客户端日志聚合结果")
public class ClientLogStatsVO {

    @Schema(description = "时间窗内总条数")
    private final long total;

    @Schema(description = "实际生效的起始时间（毫秒）")
    private final long startTime;

    @Schema(description = "实际生效的结束时间（毫秒）")
    private final long endTime;

    @Schema(description = "按级别分布，**包含条数为 0 的级别**，便于直接绘图")
    private final List<ClientLogLevelCount> levels;

    public ClientLogStatsVO(long total, long startTime, long endTime, List<ClientLogLevelCount> levels) {
        this.total = total;
        this.startTime = startTime;
        this.endTime = endTime;
        this.levels = levels;
    }

}
