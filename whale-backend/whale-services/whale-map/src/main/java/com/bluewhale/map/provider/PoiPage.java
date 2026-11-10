package com.bluewhale.map.provider;

import java.util.List;

/**
 * 地点检索分页结果。
 *
 * @param items    当前页条目
 * @param total    命中总数（供应商返回 0 时表示未知，不代表没有更多）
 * @param page     页码
 * @param size     每页条数
 * @param provider 实际提供结果的供应商名
 * @author BlueWhale
 */
public record PoiPage(List<Poi> items, int total, int page, int size, String provider) {

    /**
     * 规范化构造：条目列表不可为 null
     *
     * @param items    当前页条目
     * @param total    命中总数
     * @param page     页码
     * @param size     每页条数
     * @param provider 供应商名
     */
    public PoiPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
