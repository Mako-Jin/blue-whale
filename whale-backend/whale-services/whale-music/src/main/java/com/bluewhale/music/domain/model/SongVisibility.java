package com.bluewhale.music.domain.model;

/**
 * 歌曲可见性。
 *
 * <p>{@link #PUBLIC} 作品在租户内可见可播，允许运营事后复核并下架；
 * {@link #PRIVATE} 作品仅上传者本人可见可播（自听）。</p>
 *
 * @author BlueWhale
 */
public enum SongVisibility {

    /** 公开：租户内用户可见可播，运营可复核下架 */
    PUBLIC,

    /** 自听：仅上传者本人可见可播 */
    PRIVATE
}
