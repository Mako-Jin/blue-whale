package com.bluewhale.map.common.geo;

/**
 * 围栏判定结果。
 *
 * <p>{@code distanceMeters} 采用**有符号**距离：在围栏内为正、在外为负、恰在边界为零。
 * 这样「在范围内，距边界还有 12 米」和「超出范围 30 米」可以用一个字段表达，
 * 打卡场景里既要知道能不能打卡，也要知道还能往外走多远。</p>
 *
 * @param inside         是否在围栏内（含边界）
 * @param distanceMeters 到围栏边界的有符号距离（米）
 * @author BlueWhale
 */
public record FenceHit(boolean inside, double distanceMeters) {

    /**
     * 判定点恰在边界上
     *
     * @return 结果（在内，距离 0）
     */
    public static FenceHit onEdge() {
        return new FenceHit(true, 0.0);
    }

    /**
     * 按「到边界的绝对距离」与内外标志组装结果
     *
     * @param inside      是否在内（含边界）
     * @param absDistance 到边界的绝对距离（米）
     * @return 结果
     */
    public static FenceHit of(boolean inside, double absDistance) {
        return new FenceHit(inside, inside ? absDistance : -absDistance);
    }
}
