package com.bluewhale.music.domain.model;

/**
 * 歌曲评论状态。
 *
 * <p>默认先发后审：新评论为 {@link #VISIBLE} 并进入审核队列；
 * 配置为先审后发时新评论为 {@link #PENDING}。仅 {@link #VISIBLE} 对外展示，
 * 隐藏后立即不可见。</p>
 *
 * @author BlueWhale
 */
public enum CommentStatus {

    /** 待审核：先审后发模式下对外不可见 */
    PENDING,

    /** 可见：对外展示 */
    VISIBLE,

    /** 已隐藏：对外不可见，保留记录供追溯 */
    HIDDEN
}
