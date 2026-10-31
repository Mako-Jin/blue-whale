package com.bluewhale.music.domain.model;

import lombok.Data;

/**
 * 歌单分页查询条件。
 *
 * <p>{@code viewerId} 用于可见性过滤：公开歌单对租户内可见，私有歌单仅所有者可见；
 * {@code ownerId} 非空时只看该用户的歌单（我的歌单）。</p>
 *
 * @author BlueWhale
 */
@Data
public class PlaylistQuery {

    /** 关键词（标题 / 简介模糊匹配） */
    private String keyword;

    /** 只看指定用户的歌单（我的歌单），可为 {@code null} */
    private Long ownerId;

    /** 查看者用户 ID，用于可见性过滤 */
    private Long viewerId;

    /** 歌单类型，可为 {@code null} 表示不限 */
    private PlaylistType playlistType;

    /** 是否仅查询公开歌单（发现页 / 搜索场景） */
    private boolean publicOnly;

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 20;
}
