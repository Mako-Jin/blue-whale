package com.bluewhale.music.application.view;

import lombok.Data;

/**
 * 播放票据视图：票据 + 播放所需元数据（不暴露文件 ID）。
 *
 * @author BlueWhale
 */
@Data
public class PlayTicketView {

    /** 票据令牌 */
    private String ticket;

    /** 票据有效期（秒） */
    private int expiresIn;

    /** 歌曲 ID */
    private Long songId;

    /** 歌曲标题 */
    private String title;

    /** 歌手 ID */
    private Long artistId;

    /** 封面文件 ID（展示用，播放地址仍走票据代理） */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 歌词纯文本（LRC 或纯文本，前端按需解析） */
    private String lyricText;

    /** 流式播放地址（含票据） */
    private String streamUrl;
}
