package com.bluewhale.map.vo;

import lombok.Data;

/**
 * 围栏命中结果。
 *
 * <p>{@code distanceMeters} 为有符号距离：在内为正、在外为负。
 * 打卡场景靠它区分「刚进范围」和「稳稳在范围内」，也能直接提示「还差 30 米」。</p>
 *
 * @author BlueWhale
 */
@Data
public class FenceHitVO {

    /** 围栏ID */
    private Long id;

    /** 围栏名称 */
    private String name;

    /** 围栏类型：CIRCLE / POLYGON */
    private String fenceType;

    /** 绑定业务类型 */
    private String bizType;

    /** 绑定业务对象ID */
    private String bizId;

    /** 是否在围栏内（含边界） */
    private boolean inside;

    /** 到围栏边界的有符号距离（米，内正外负） */
    private double distanceMeters;
}
