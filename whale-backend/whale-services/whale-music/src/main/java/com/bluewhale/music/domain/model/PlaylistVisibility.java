package com.bluewhale.music.domain.model;

/**
 * 歌单可见性。
 *
 * <p>{@link #PUBLIC} 歌单在租户内可见；{@link #PRIVATE} 歌单仅所有者可见。
 * 官方歌单固定为 {@link #PUBLIC} 且用户只读。</p>
 *
 * @author BlueWhale
 */
public enum PlaylistVisibility {

    /** 公开：租户内可见 */
    PUBLIC,

    /** 私有：仅所有者可见 */
    PRIVATE
}
