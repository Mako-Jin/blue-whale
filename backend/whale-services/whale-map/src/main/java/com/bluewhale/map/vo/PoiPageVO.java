package com.bluewhale.map.vo;

import lombok.Data;

import java.util.List;

/**
 * 地点检索分页视图。
 *
 * @author BlueWhale
 */
@Data
public class PoiPageVO {

    /** 当前页条目 */
    private List<PoiVO> items;

    /** 命中总数（0 表示供应商未返回总数，不代表没有更多） */
    private int total;

    /** 页码 */
    private int page;

    /** 每页条数 */
    private int size;

    /** 实际提供结果的供应商 */
    private String provider;
}
