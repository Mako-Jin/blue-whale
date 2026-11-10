package com.bluewhale.music.application.command;

import lombok.Data;

/**
 * 歌单分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class PlaylistQueryCommand {

    /** 关键词（标题 / 简介模糊匹配） */
    private String keyword;

    /** 是否只看自己的歌单 */
    private boolean mine;

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数，最大 200 */
    private long size = 20;
}
