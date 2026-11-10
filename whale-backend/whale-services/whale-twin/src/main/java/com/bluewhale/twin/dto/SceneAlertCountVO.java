package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景维度未恢复告警计数（Wave 6.09）：场景列表逐行角标的数据载体。
 *
 * <p>与平台级统计 {@link TwinAlertSummaryVO} 同口径（{@code resolved = 0}），
 * 只是按场景拆分；一次性分组取回后由服务装配进 {@link SceneVO}，不下发嵌套对象。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneAlertCountVO {

    /** 未恢复告警数 */
    private long unresolved;

    /** 未恢复中 critical（严重）数量 */
    private long critical;

    /** 未恢复且未确认数量（仍需人工知悉） */
    private long unacked;
}
