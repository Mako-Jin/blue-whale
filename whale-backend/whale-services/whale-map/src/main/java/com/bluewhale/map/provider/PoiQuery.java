package com.bluewhale.map.provider;

/**
 * 地点检索查询条件。
 *
 * @param keyword 关键词（必填）
 * @param city    限定城市（可空）
 * @param page    页码（1 起）
 * @param size    每页条数（1–50）
 * @author BlueWhale
 */
public record PoiQuery(String keyword, String city, int page, int size) {

    /** 默认每页条数 */
    private static final int DEFAULT_SIZE = 20;

    /** 每页条数上限：供应商侧上限普遍为 50，超过会被静默截断成 50 却让调用方以为拿到了全部 */
    private static final int MAX_SIZE = 50;

    /**
     * 校验并构造查询条件
     *
     * @param keyword 关键词
     * @param city    限定城市
     * @param page    页码
     * @param size    每页条数
     */
    public PoiQuery {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        keyword = keyword.trim();
        city = city == null || city.isBlank() ? null : city.trim();
        page = Math.max(page, 1);
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        size = Math.min(size, MAX_SIZE);
    }
}
