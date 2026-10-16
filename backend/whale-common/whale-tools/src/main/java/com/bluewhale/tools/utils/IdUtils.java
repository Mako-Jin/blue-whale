package com.bluewhale.tools.utils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 简易 ID 生成工具类
 * 注意：nextId() 为时间戳 + 随机数的简易实现，非严格雪花算法，适用于对趋势有序性要求不高的场景
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
     * 生成无横线的 UUID 字符串
     */
    public static String nextUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成简易分布式 ID：毫秒时间戳左移 20 位 + 20 位随机数，保证正 long 范围内可用
     */
    public static long nextId() {
        long timestamp = System.currentTimeMillis();
        long random = ThreadLocalRandom.current().nextLong(RANDOM_MAX);
        return (timestamp << RANDOM_BITS) | random;
    }
}
