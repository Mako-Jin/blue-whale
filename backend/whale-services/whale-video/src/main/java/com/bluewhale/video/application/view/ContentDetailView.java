package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容详情视图：在列表视图基础上补充互动状态与播放授权入口。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentDetailView extends PublicContentView {

    private static final long serialVersionUID = 1L;

    /** 是否允许评论（私密内容或已下架内容为 {@code false}） */
    private boolean commentable;

    /** 当前用户是否已点赞 */
    private boolean liked;

    /** 当前用户是否已收藏 */
    private boolean favorited;

    /** 当前用户观看进度（毫秒，未观看为 0） */
    private Long progressMs;

    /** 播放票据申请地址 */
    private String ticketUrl;
}
