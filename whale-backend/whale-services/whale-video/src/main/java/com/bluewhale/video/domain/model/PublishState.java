package com.bluewhale.video.domain.model;

/**
 * 发布状态：草稿 → 待审 → 已发布 → 已下线。
 *
 * <p>版权下架不新增状态，而是强制切到 {@link #OFFLINE} 并置版权状态为
 * {@code TAKEDOWN}；已发布状态重复发布幂等，仅刷新上架时间。</p>
 *
 * @author BlueWhale
 */
public enum PublishState {

    /** 草稿：仅上传者可见，不可播放 */
    DRAFT,

    /** 待审核：运营可见，用户侧不可见 */
    PENDING_REVIEW,

    /** 已发布：用户侧可见（私密内容仅本人可见） */
    PUBLISHED,

    /** 已下线：用户侧不可见，不可签发播放票据 */
    OFFLINE;

    /**
     * 解析发布状态文本。
     *
     * @param value 存储 / 请求文本
     * @return 发布状态；无法识别回落 {@link #DRAFT}
     */
    public static PublishState parse(String value) {
        for (PublishState item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return DRAFT;
    }
}
