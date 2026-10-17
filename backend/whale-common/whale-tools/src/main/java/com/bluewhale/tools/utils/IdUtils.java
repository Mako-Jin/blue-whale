package com.bluewhale.tools.utils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 简易 ID 生成工具类
 * <p>注意：{@link #nextId()} 为「毫秒时间戳左移 20 位 + 20 位随机数」的简易实现，
 * <b>不是严格雪花算法</b>（无 workerId 位、无同毫秒自增序列），仅适用于对趋势有序性
 * 要求不高的非主键场景（如日志号、临时批次号）。</p>
 * <p><b>实体主键禁止使用本类</b>：实体统一继承 {@code BaseEntity}，其主键由
 * MyBatis-Plus 内置雪花算法（{@code IdType.ASSIGN_ID}）生成，无需业务代码干预；
 * {@link #nextUUID()} 可用于业务流水号、幂等 token、TraceId 等字符串场景。</p>
 *
 * @author BlueWhale
 */
public final class IdUtils {

    /** 随机数占用位数 */
    private static final int RANDOM_BITS = 20;

    /** 随机数取值上限 */
    private static final long RANDOM_MAX = 1L << RANDOM_BITS;

    private IdUtils() {
    }

    /**
     * 生成无横线的 UUID 字符串（业务流水号 / 幂等 token / TraceId）
     */
    public static String nextUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成简易分布式 ID：毫秒时间戳左移 20 位 + 20 位随机数，保证正 long 范围内可用
     * <p><b>非严格雪花，勿用于实体主键</b>（同毫秒内无自增序列，重启/多实例不保证严格趋势递增）。</p>
     */
    public static long nextId() {
        long timestamp = System.currentTimeMillis();
        long random = ThreadLocalRandom.current().nextLong(RANDOM_MAX);
        return (timestamp << RANDOM_BITS) | random;
    }
}
