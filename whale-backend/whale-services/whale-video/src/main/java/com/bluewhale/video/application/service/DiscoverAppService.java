package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.view.FeedView;
import com.bluewhale.video.application.view.HomeView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.RankingView;

/**
 * 发现应用服务：首页聚合与站内搜索（带短时缓存与抖动）。
 *
 * @author BlueWhale
 */
public interface DiscoverAppService {

    /**
     * 首页聚合（短视频 / 视频 / 电影最新各 8 条 + 分类标签）。
     *
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 首页视图
     */
    HomeView home(Long userId);

    /**
     * 站内搜索（标题 / 副标题模糊匹配，仅可见已发布内容）。
     *
     * @param keyword 关键词
     * @param current 当前页
     * @param size 每页条数
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 用户侧分页结果
     */
    PageResult<PublicContentView> search(String keyword, long current, long size, Long userId);

    /**
     * 短视频 feed（键集分页：最新 / 热门 / 分类，首屏 30 秒缓存）。
     *
     * @param sort 排序：LATEST / HOT，非法值回落最新
     * @param categoryId 分类 ID，{@code null} 或 0 表示全部
     * @param cursor 游标，空白表示首屏
     * @param size 每批条数（0 表示默认 10，上限 50）
     * @param userId 当前用户 ID，可为 {@code null}
     * @return feed 视图
     */
    FeedView shortFeed(String sort, Long categoryId, String cursor, long size, Long userId);

    /**
     * 发现榜单：热门榜与新片榜（可选内容类型）。
     *
     * @param type 内容类型，空白表示跨类型
     * @param size 每榜条数（0 表示默认 10，上限 50）
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 榜单视图
     */
    RankingView rankings(String type, long size, Long userId);
}
