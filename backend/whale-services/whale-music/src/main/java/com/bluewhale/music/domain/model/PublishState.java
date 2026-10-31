package com.bluewhale.music.domain.model;

/**
 * 歌曲发布状态。
 *
 * <p>状态流转：{@link #DRAFT} → {@link #PUBLISHED} → {@link #OFFLINE}；
 * 版权下架通过 {@link CopyrightState#TAKEDOWN} 强制进入 {@link #OFFLINE}。</p>
 *
 * @author BlueWhale
 */
public enum PublishState {

    /** 草稿：上传后尚未上架，仅上传者与运营可见 */
    DRAFT,

    /** 已上架：公开作品租户内可见可播，自听作品仅上传者可见 */
    PUBLISHED,

    /** 已下架：任何用户不可见不可播 */
    OFFLINE
}
