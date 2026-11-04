package com.bluewhale.crm.interfaces.support;

/**
 * 分页参数归一化：兼容后端 {@code current/size} 与前端 {@code pageNum/pageSize} 两套命名。
 *
 * @author BlueWhale
 */
public final class PageParams {

    /** 默认页码 */
    private static final long DEFAULT_CURRENT = 1L;

    /** 默认每页条数 */
    private static final long DEFAULT_SIZE = 10L;

    /** 单页上限 */
    private static final long MAX_SIZE = 200L;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private PageParams() {
    }

    /**
     * 解析页码。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @return 合法页码
     */
    public static long current(Long current, Long pageNum) {
        long value = current != null ? current : (pageNum != null ? pageNum : DEFAULT_CURRENT);
        return value < 1 ? DEFAULT_CURRENT : value;
    }

    /**
     * 解析每页条数。
     *
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 合法条数
     */
    public static long size(Long size, Long pageSize) {
        long value = size != null ? size : (pageSize != null ? pageSize : DEFAULT_SIZE);
        return value < 1 ? DEFAULT_SIZE : Math.min(value, MAX_SIZE);
    }
}
