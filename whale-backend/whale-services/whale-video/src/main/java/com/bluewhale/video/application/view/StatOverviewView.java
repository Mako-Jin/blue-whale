package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 运营统计概览视图（内容 / 播放 / 审核 / 授权到期）。
 *
 * @author BlueWhale
 */
@Data
public class StatOverviewView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容总数（含草稿与已下线） */
    private long totalContent;

    /** 已发布内容数 */
    private long publishedContent;

    /** 短视频数 */
    private long shortVideoCount;

    /** 视频数 */
    private long videoCount;

    /** 电影数 */
    private long movieCount;

    /** 播放次数总和 */
    private long totalPlayCount;

    /** 待审核评论数 */
    private long pendingCommentCount;

    /** 授权即将到期（含已到期）的已发布电影数 */
    private long expiringLicenseCount;

    /** 片单总数 */
    private long collectionCount;

    /** 创作者总数 */
    private long creatorCount;
}
