package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 发现榜单视图：热门榜与新片榜。
 *
 * @author BlueWhale
 */
@Data
public class RankingView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 榜单内容类型；为空表示跨类型 */
    private String contentType;

    /** 热门榜（按播放 / 点赞排序） */
    private List<PublicContentView> hot;

    /** 新片榜（按上架时间倒序） */
    private List<PublicContentView> latest;
}
