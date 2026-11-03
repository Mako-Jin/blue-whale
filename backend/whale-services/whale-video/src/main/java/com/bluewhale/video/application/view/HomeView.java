package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 首页聚合视图（最新短视频 / 视频 / 电影 + 分类 + 标签）。
 *
 * @author BlueWhale
 */
@Data
public class HomeView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 短视频推荐列表 */
    private List<PublicContentView> shorts;

    /** 视频推荐列表 */
    private List<PublicContentView> videos;

    /** 电影推荐列表 */
    private List<PublicContentView> movies;

    /** 分类列表 */
    private List<CategoryView> categories;

    /** 标签列表 */
    private List<TagView> tags;
}
