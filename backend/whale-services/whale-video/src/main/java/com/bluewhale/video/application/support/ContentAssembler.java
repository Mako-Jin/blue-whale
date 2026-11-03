package com.bluewhale.video.application.support;

import com.bluewhale.video.application.view.ContentDetailView;
import com.bluewhale.video.application.view.ContentView;
import com.bluewhale.video.application.view.OwnerContentView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.domain.model.Content;

import java.util.List;

/**
 * 内容视图组装器（公开视图不暴露文件 ID）。
 *
 * @author BlueWhale
 */
public final class ContentAssembler {

    /** 封面代理地址前缀 */
    public static final String COVER_URL_PREFIX = "/video/content/";

    /** 封面代理地址后缀 */
    public static final String COVER_URL_SUFFIX = "/cover";

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private ContentAssembler() {
    }

    /**
     * 构造封面代理地址。
     *
     * @param contentId 内容 ID
     * @param coverFileId 封面文件 ID
     * @return 代理地址；无封面返回 {@code null}
     */
    public static String coverUrl(Long contentId, Long coverFileId) {
        if (contentId == null || coverFileId == null || coverFileId <= 0) {
            return null;
        }
        return COVER_URL_PREFIX + contentId + COVER_URL_SUFFIX;
    }

    /**
     * 组装用户侧内容视图。
     *
     * @param content 内容
     * @param tagNames 标签名称
     * @param creatorName 创作者昵称，可为 {@code null}
     * @return 用户侧视图
     */
    public static PublicContentView toPublic(Content content, List<String> tagNames, String creatorName) {
        PublicContentView view = new PublicContentView();
        view.setId(content.getId());
        view.setContentType(content.getContentType() == null ? null : content.getContentType().name());
        view.setTitle(content.getTitle());
        view.setSubtitle(content.getSubtitle());
        view.setDescription(content.getDescription());
        view.setDurationMs(content.getDurationMs());
        view.setWidth(content.getWidth());
        view.setHeight(content.getHeight());
        view.setFormat(content.getFormat());
        view.setCategoryId(content.getCategoryId());
        view.setCreatorId(content.getCreatorId());
        view.setCreatorName(creatorName);
        view.setSeriesId(content.getSeriesId());
        view.setEpisodeNo(content.getEpisodeNo());
        view.setVisibility(content.getVisibility() == null ? null : content.getVisibility().name());
        view.setPlayCount(content.getPlayCount());
        view.setLikeCount(content.getLikeCount());
        view.setFavoriteCount(content.getFavoriteCount());
        view.setCommentCount(content.getCommentCount());
        view.setTags(tagNames);
        view.setCoverUrl(coverUrl(content.getId(), content.getCoverFileId()));
        view.setPlayable(content.playable());
        view.setPublishedAt(content.getPublishedAt());
        return view;
    }

    /**
     * 组装上传者侧内容视图（在用户侧视图基础上补充发布与版权状态）。
     *
     * @param content 内容
     * @param tagNames 标签名称
     * @return 上传者侧视图
     */
    public static OwnerContentView toOwner(Content content, List<String> tagNames) {
        PublicContentView base = toPublic(content, tagNames, null);
        OwnerContentView view = new OwnerContentView();
        view.setId(base.getId());
        view.setContentType(base.getContentType());
        view.setTitle(base.getTitle());
        view.setSubtitle(base.getSubtitle());
        view.setDescription(base.getDescription());
        view.setDurationMs(base.getDurationMs());
        view.setWidth(base.getWidth());
        view.setHeight(base.getHeight());
        view.setFormat(base.getFormat());
        view.setCategoryId(base.getCategoryId());
        view.setCreatorId(base.getCreatorId());
        view.setCreatorName(base.getCreatorName());
        view.setSeriesId(base.getSeriesId());
        view.setEpisodeNo(base.getEpisodeNo());
        view.setVisibility(base.getVisibility());
        view.setPlayCount(base.getPlayCount());
        view.setLikeCount(base.getLikeCount());
        view.setFavoriteCount(base.getFavoriteCount());
        view.setCommentCount(base.getCommentCount());
        view.setTags(base.getTags());
        view.setCoverUrl(base.getCoverUrl());
        view.setPlayable(base.isPlayable());
        view.setPublishedAt(base.getPublishedAt());
        view.setPublishState(content.getPublishState() == null ? null : content.getPublishState().name());
        view.setCopyrightState(content.getCopyrightState() == null ? null : content.getCopyrightState().name());
        view.setTakedownReason(content.getTakedownReason());
        view.setCreateTime(content.getCreateTime());
        return view;
    }

    /**
     * 复制用户侧基础字段，供电影 / 片单等扩展视图复用。
     *
     * @param target 目标视图
     * @param base 基础视图
     */
    public static void copyPublic(PublicContentView target, PublicContentView base) {
        target.setId(base.getId());
        target.setContentType(base.getContentType());
        target.setTitle(base.getTitle());
        target.setSubtitle(base.getSubtitle());
        target.setDescription(base.getDescription());
        target.setDurationMs(base.getDurationMs());
        target.setWidth(base.getWidth());
        target.setHeight(base.getHeight());
        target.setFormat(base.getFormat());
        target.setCategoryId(base.getCategoryId());
        target.setCreatorId(base.getCreatorId());
        target.setCreatorName(base.getCreatorName());
        target.setSeriesId(base.getSeriesId());
        target.setEpisodeNo(base.getEpisodeNo());
        target.setVisibility(base.getVisibility());
        target.setPlayCount(base.getPlayCount());
        target.setLikeCount(base.getLikeCount());
        target.setFavoriteCount(base.getFavoriteCount());
        target.setCommentCount(base.getCommentCount());
        target.setTags(base.getTags());
        target.setCoverUrl(base.getCoverUrl());
        target.setPlayable(base.isPlayable());
        target.setPublishedAt(base.getPublishedAt());
    }

    /**
     * 复制详情字段（互动状态与播放授权入口）。
     *
     * @param target 目标视图
     * @param base 基础详情视图
     */
    public static void copyDetail(ContentDetailView target, ContentDetailView base) {
        copyPublic(target, base);
        target.setCommentable(base.isCommentable());
        target.setLiked(base.isLiked());
        target.setFavorited(base.isFavorited());
        target.setProgressMs(base.getProgressMs());
        target.setTicketUrl(base.getTicketUrl());
    }

    /**
     * 复制运营侧内容字段，供电影等扩展视图复用。
     *
     * @param target 目标视图
     * @param base 基础视图
     */
    public static void copyAdmin(ContentView target, ContentView base) {
        target.setId(base.getId());
        target.setContentType(base.getContentType());
        target.setTitle(base.getTitle());
        target.setSubtitle(base.getSubtitle());
        target.setDescription(base.getDescription());
        target.setVideoFileId(base.getVideoFileId());
        target.setCoverFileId(base.getCoverFileId());
        target.setDurationMs(base.getDurationMs());
        target.setWidth(base.getWidth());
        target.setHeight(base.getHeight());
        target.setSizeBytes(base.getSizeBytes());
        target.setUploaderId(base.getUploaderId());
        target.setCreatorId(base.getCreatorId());
        target.setCategoryId(base.getCategoryId());
        target.setSeriesId(base.getSeriesId());
        target.setEpisodeNo(base.getEpisodeNo());
        target.setVisibility(base.getVisibility());
        target.setPublishState(base.getPublishState());
        target.setCopyrightState(base.getCopyrightState());
        target.setSource(base.getSource());
        target.setCopyrightExpireAt(base.getCopyrightExpireAt());
        target.setTakedownReason(base.getTakedownReason());
        target.setTakedownAt(base.getTakedownAt());
        target.setPlayCount(base.getPlayCount());
        target.setLikeCount(base.getLikeCount());
        target.setFavoriteCount(base.getFavoriteCount());
        target.setCommentCount(base.getCommentCount());
        target.setTagIds(base.getTagIds());
        target.setPublishedAt(base.getPublishedAt());
        target.setCreateTime(base.getCreateTime());
    }

    /**
     * 组装运营侧内容视图（含内部文件引用与审核字段）。
     *
     * @param content 内容
     * @param tagIds 标签 ID 列表
     * @return 运营侧视图
     */
    public static ContentView toAdmin(Content content, List<Long> tagIds) {
        ContentView view = new ContentView();
        view.setId(content.getId());
        view.setContentType(content.getContentType() == null ? null : content.getContentType().name());
        view.setTitle(content.getTitle());
        view.setSubtitle(content.getSubtitle());
        view.setDescription(content.getDescription());
        view.setVideoFileId(content.getVideoFileId());
        view.setCoverFileId(content.getCoverFileId());
        view.setDurationMs(content.getDurationMs());
        view.setWidth(content.getWidth());
        view.setHeight(content.getHeight());
        view.setSizeBytes(content.getSizeBytes());
        view.setUploaderId(content.getUploaderId());
        view.setCreatorId(content.getCreatorId());
        view.setCategoryId(content.getCategoryId());
        view.setSeriesId(content.getSeriesId());
        view.setEpisodeNo(content.getEpisodeNo());
        view.setVisibility(content.getVisibility() == null ? null : content.getVisibility().name());
        view.setPublishState(content.getPublishState() == null ? null : content.getPublishState().name());
        view.setCopyrightState(content.getCopyrightState() == null ? null : content.getCopyrightState().name());
        view.setSource(content.getSource() == null ? null : content.getSource().name());
        view.setCopyrightExpireAt(content.getCopyrightExpireAt());
        view.setTakedownReason(content.getTakedownReason());
        view.setTakedownAt(content.getTakedownAt());
        view.setPlayCount(content.getPlayCount());
        view.setLikeCount(content.getLikeCount());
        view.setFavoriteCount(content.getFavoriteCount());
        view.setCommentCount(content.getCommentCount());
        view.setTagIds(tagIds);
        view.setPublishedAt(content.getPublishedAt());
        view.setCreateTime(content.getCreateTime());
        return view;
    }
}
