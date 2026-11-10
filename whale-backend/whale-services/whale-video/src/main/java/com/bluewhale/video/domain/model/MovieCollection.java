package com.bluewhale.video.domain.model;

import com.bluewhale.video.domain.exception.VideoErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电影片单聚合根：运营维护的电影合集。
 *
 * <p>状态流转：草稿（DRAFT）→ 上架（PUBLISHED）→ 下架（OFFLINE）；
 * 条目引用 {@code vd_content} 中的电影内容，同一片单同一电影唯一，
 * 单片单最多 {@link #MAX_ITEMS} 部；片单下架不影响电影自身状态。</p>
 *
 * @author BlueWhale
 */
@Data
public class MovieCollection {

    /** 标题最大长度 */
    public static final int TITLE_MAX_LENGTH = 200;

    /** 单片单最大条目数 */
    public static final int MAX_ITEMS = 200;

    /** 主键 */
    private Long id;

    /** 片单标题 */
    private String title;

    /** 片单简介 */
    private String description;

    /** 封面文件 ID（whale-file） */
    private Long coverFileId;

    /** 运营排序（升序） */
    private Integer sortNo;

    /** 发布状态 */
    private PublishState publishState;

    /** 条目数 */
    private Integer itemCount;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 设置并校验片单标题。
     *
     * @param value 标题文本
     */
    public void retitle(String value) {
        if (value == null || value.isBlank() || value.trim().length() > TITLE_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_TITLE_INVALID);
        }
        title = value.trim();
    }

    /**
     * 上架片单：已上架时幂等（仅刷新上架时间）。
     */
    public void publish() {
        if (publishState == PublishState.PUBLISHED) {
            publishedAt = LocalDateTime.now();
            return;
        }
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_TITLE_INVALID);
        }
        publishState = PublishState.PUBLISHED;
        publishedAt = LocalDateTime.now();
    }

    /**
     * 下架片单：草稿不可下架，重复下架幂等。
     */
    public void offline() {
        if (publishState == PublishState.DRAFT || publishState == PublishState.PENDING_REVIEW) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        publishState = PublishState.OFFLINE;
    }

    /**
     * 判断片单是否对用户侧可见。
     *
     * @return 已上架返回 {@code true}
     */
    public boolean published() {
        return publishState == PublishState.PUBLISHED;
    }

    /**
     * 刷新条目数（不小于 0）。
     *
     * @param count 条目数
     */
    public void refreshItemCount(int count) {
        itemCount = Math.max(count, 0);
    }

    /**
     * 校验是否还能继续加片。
     *
     * @param currentCount 当前条目数
     */
    public void assertCanAddItem(int currentCount) {
        if (currentCount >= MAX_ITEMS) {
            throw VideoErrorCode.exception(VideoErrorCode.COLLECTION_ITEM_CONFLICT);
        }
    }
}
