package com.bluewhale.lms.exam.domain.model;

/**
 * 抽题排序键（保证「同一 seed + 同一题库 → 同一套题」）。
 *
 * <p>不用 {@code Random}：不同 JDK 版本、不同机器上同一 seed 的随机序列不保证一致，
 * 且题库增量后无法解释「为什么这次抽到的题变了」。这里用纯哈希派生排序键，
 * 结果只取决于 {@code seed} 与 {@code questionId}，可跨环境复现、可单测断言。</p>
 *
 * <p>实现取 splitmix64 风格的雪崩混合：<b>不能</b>写成 {@code seed * a + id * b}
 * 这类线性组合——那样排序键与题目 ID 单调相关，抽题会固定命中 ID 最小的若干题，
 * seed 形同虚设（该缺陷由 {@code PickOrderTest} 的「不同 seed 排出不同题序」用例抓出）。</p>
 *
 * @author BlueWhale
 */
public final class PickOrder {

    /** 取模基数（质数，降低碰撞聚集） */
    private static final long MODULUS = 1_000_003L;

    /** 混合常量一（2^64 / 黄金比例取整） */
    private static final long MIX_ONE = 0x9E3779B97F4A7C15L;

    /** 混合常量二 */
    private static final long MIX_TWO = 0xBF58476D1CE4E5B9L;

    /** 混合常量三 */
    private static final long MIX_THREE = 0x94D049BB133111EBL;

    /** 右移位数一 */
    private static final int SHIFT_ONE = 30;

    /** 右移位数二 */
    private static final int SHIFT_TWO = 27;

    /** 右移位数三 */
    private static final int SHIFT_THREE = 31;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private PickOrder() {
    }

    /**
     * 计算题目在本次抽题中的排序键。
     *
     * @param seed 抽题种子
     * @param questionId 题目 ID
     * @return 排序键（0 到 1000002）
     */
    public static long keyOf(String seed, Long questionId) {
        long seedHash = seed == null ? 0L : seed.hashCode();
        long mixed = (seedHash * MIX_ONE) ^ ((questionId == null ? 0L : questionId) * MIX_TWO);
        mixed ^= mixed >>> SHIFT_ONE;
        mixed *= MIX_TWO;
        mixed ^= mixed >>> SHIFT_TWO;
        mixed *= MIX_THREE;
        mixed ^= mixed >>> SHIFT_THREE;
        return Math.floorMod(mixed, MODULUS);
    }
}
