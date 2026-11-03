package com.bluewhale.video.interfaces.support;

/**
 * 分页参数归一化：同时兼容后端 {@code current/size} 与前端契约 {@code pageNum/pageSize}。
 *
 * @author BlueWhale
 */
public final class PageParams {

    /** 默认页码 */
    public static final long DEFAULT_CURRENT = 1L;

    /** 默认每页条数 */
    public static final long DEFAULT_SIZE = 20L;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private PageParams() {
    }

    /**
     * 解析页码：{@code current} 优先，其次 {@code pageNum}，非法值回落 1。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @return 归一化页码（不小于 1）
     */
    public static long current(Long current, Long pageNum) {
        Long value = firstPositive(current, pageNum);
        return value == null ? DEFAULT_CURRENT : value;
    }

    /**
     * 解析每页条数：{@code size} 优先，其次 {@code pageSize}，非法值回落 20。
     *
     * @param size 后端风格每页条数
     * @param pageSize 前端风格每页条数
     * @return 归一化条数（不小于 1）
     */
    public static long size(Long size, Long pageSize) {
        Long value = firstPositive(size, pageSize);
        return value == null ? DEFAULT_SIZE : value;
    }

    /**
     * 取第一个正数参数。
     *
     * @param first 首选值
     * @param second 备选值
     * @return 第一个正值；都不合法返回 {@code null}
     */
    private static Long firstPositive(Long first, Long second) {
        if (first != null && first > 0) {
            return first;
        }
        if (second != null && second > 0) {
            return second;
        }
        return null;
    }
}
