package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跨场景告警总览统计（Wave 6.08 M1）：平台级告警态势一屏数据。
 *
 * <p>口径均为「未恢复」（{@code resolved = 0}）的行数：{@code unresolved} 为总数，
 * {@code critical}/{@code warn} 按级别拆分，{@code unacked} 为其中尚未人工确认的数量。
 * 与场景维度接口（{@code /twin/scenes/{id}/alerts}）同源同表，不引入额外状态。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinAlertSummaryVO {

    /** 未恢复告警总数 */
    private long unresolved;

    /** 未恢复中 critical（严重）数量 */
    private long critical;

    /** 未恢复中 warn（预警）数量 */
    private long warn;

    /** 未恢复且未确认数量（仍需人工知悉） */
    private long unacked;
}
