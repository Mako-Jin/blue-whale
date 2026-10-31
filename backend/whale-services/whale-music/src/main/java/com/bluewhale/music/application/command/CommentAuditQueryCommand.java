package com.bluewhale.music.application.command;

import lombok.Data;

/**
 * 评论审核分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class CommentAuditQueryCommand {

    /** 歌曲 ID，可选 */
    private Long songId;

    /** 评论状态：PENDING / VISIBLE / HIDDEN，可选 */
    private String status;

    /** 正文关键词，可选 */
    private String keyword;

    /** 页码 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
