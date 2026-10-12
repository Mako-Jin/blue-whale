package com.bluewhale.sts.audit.auditlog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * 审计聚合结果。
 *
 * <p>把实际生效的时间窗一并回传：调用方没传时间时服务端会补默认值（7 天），
 * 若不回传"到底查的是哪一段"，用户看到总数偏小时会怀疑是丢了数据，
 * 而不是意识到自己查的是最近 7 天。
 *
 * <p>同样回传 {@code failureTotal}：审计面板上"今天有 12 次操作"与
 * "今天有 12 次操作、其中 3 次失败"是两件完全不同的事，而总数里看不出后者。
 */
@Getter
@Schema(name = "AuditStatsVO", description = "审计聚合结果")
public class AuditStatsVO {

    @Schema(description = "时间窗内总条数")
    private final long total;

    @Schema(description = "其中结果为 FAILURE 的条数")
    private final long failureTotal;

    @Schema(description = "实际生效的起始时间（毫秒）")
    private final long startTime;

    @Schema(description = "实际生效的结束时间（毫秒）")
    private final long endTime;

    @Schema(description = "按动作分布，只包含出现过的动作")
    private final List<AuditActionCount> actions;

    public AuditStatsVO(long total, long failureTotal, long startTime, long endTime,
                        List<AuditActionCount> actions) {
        this.total = total;
        this.failureTotal = failureTotal;
        this.startTime = startTime;
        this.endTime = endTime;
        this.actions = actions;
    }

}
