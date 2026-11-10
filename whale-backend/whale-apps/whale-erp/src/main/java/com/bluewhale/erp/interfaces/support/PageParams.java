package com.bluewhale.erp.interfaces.support;

/**
 * 分页参数兼容（后端风格 {@code current/size} 与前端风格 {@code pageNum/pageSize}）。
 *
 * @author BlueWhale
 */
public final class PageParams {

    /** 默认每页条数 */
    private static final long DEFAULT_SIZE = 20L;

    /** 每页条数上限 */
    private static final long MAX_SIZE = 100L;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private PageParams() {
    }

    /**
     * 解析当前页。
     *
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @return 合法页码（默认 1）
     */
    public static Long current(Long current, Long pageNum) {
        if (current != null && current > 0) {
            return current;
        }
        if (pageNum != null && pageNum > 0) {
            return pageNum;
        }
        return 1L;
    }

    /**
     * 解析每页条数。
     *
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 合法条数（默认 20，上限 100）
     */
    public static Long size(Long size, Long pageSize) {
        Long value = size != null && size > 0 ? size : pageSize;
        if (value == null || value < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(value, MAX_SIZE);
    }
}
