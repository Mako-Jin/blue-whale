package com.bluewhale.music.domain.model;

/**
 * 歌单类型。
 *
 * @author BlueWhale
 */
public enum PlaylistType {

    /** 用户歌单：用户自建，可编辑与排序 */
    USER,

    /** 官方歌单：运营维护，用户只读 */
    OFFICIAL
}
