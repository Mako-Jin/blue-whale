package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 系列分页查询条件（运营列表 / 用户侧系列选择共用）。
 *
 * @author BlueWhale
 */
@Data
public class SeriesQuery {

    /** 标题关键词 */
    private String keyword;

    /** 内容类型筛选 */
    private ContentType contentType;

    /** 发布状态筛选 */
    private PublishState publishState;

    /** 分类筛选 */
    private Long categoryId;

    /** 创作者筛选 */
    private Long creatorId;

    /** 当前页（从 1 开始） */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
