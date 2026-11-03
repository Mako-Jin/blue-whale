package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 内容分页查询条件（用户侧 / 运营侧 / 我的作品共用）。
 *
 * @author BlueWhale
 */
@Data
public class ContentQuery {

    /** 内容类型筛选 */
    private ContentType contentType;

    /** 分类筛选 */
    private Long categoryId;

    /** 标签筛选 */
    private Long tagId;

    /** 上传者筛选（我的作品 / 运营按人查询） */
    private Long uploaderId;

    /** 创作者筛选 */
    private Long creatorId;

    /** 系列筛选 */
    private Long seriesId;

    /** 发布状态筛选 */
    private PublishState publishState;

    /** 可见性筛选 */
    private ContentVisibility visibility;

    /** 关键词（标题 / 副标题模糊匹配） */
    private String keyword;

    /** 排序：LATEST 最新 / HOT 最热 */
    private String sortBy;

    /** 当前页（从 1 开始） */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
