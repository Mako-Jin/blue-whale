package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * feed 键集查询条件（短视频最新 / 热门 / 分类流）。
 *
 * <p>与分页查询不同，feed 使用游标定位：{@code cursor} 为空表示首屏，
 * {@code limit} 为期望条数（应用层通常多取 1 条用于判断是否还有下一页）。</p>
 *
 * @author BlueWhale
 */
@Data
public class FeedQuery {

    /** 内容类型（短视频 feed 固定为 SHORT_VIDEO） */
    private ContentType contentType;

    /** 分类筛选，{@code null} 或 0 表示全部 */
    private Long categoryId;

    /** 排序：LATEST 最新 / HOT 最热 */
    private String sortBy;

    /** 游标，{@code null} 表示首屏 */
    private FeedCursor cursor;

    /** 期望条数 */
    private long limit;
}
