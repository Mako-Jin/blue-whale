package com.bluewhale.mall.interfaces.support;

/**
 * 分页参数归一化。
 *
 * <p>前端历史上有两套命名（{@code current/size} 与 {@code pageNum/pageSize}），
 * 两套都收但只认一套值：优先取后方（前端风格），便于逐步收敛。</p>
 *
 * @author BlueWhale
 */
public final class PageParams {

    /** 默认页码 */
    private static final long DEFAULT_CURRENT = 1L;

    /** 默认每页条数 */
    private static final long DEFAULT_SIZE = 20L;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private PageParams() {
    }

    /**
     * 归一化页码。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @return 不小于 1 的页码
     */
    public static long current(Long current, Long pageNum) {
        Long value = pageNum != null ? pageNum : current;
        return value == null || value < 1L ? DEFAULT_CURRENT : value;
    }

    /**
     * 归一化每页条数。
     *
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param limit 单页上限
     * @return 1 到上限之间的条数
     */
    public static long size(Long size, Long pageSize, int limit) {
        Long value = pageSize != null ? pageSize : size;
        if (value == null || value < 1L) {
            return Math.min(DEFAULT_SIZE, limit);
        }
        return Math.min(value, limit);
    }
}
