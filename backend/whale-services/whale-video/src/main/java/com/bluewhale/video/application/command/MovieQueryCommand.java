package com.bluewhale.video.application.command;

import lombok.Data;

/**
 * 电影分页查询命令（用户侧 / 运营侧共用）。
 *
 * @author BlueWhale
 */
@Data
public class MovieQueryCommand {

    /** 关键词（标题 / 原始片名模糊匹配） */
    private String keyword;

    /** 分类 ID */
    private Long categoryId;

    /** 创作者 ID */
    private Long creatorId;

    /** 发布状态（仅运营可用） */
    private String publishState;

    /** 排序：LATEST 最新 / HOT 最热 */
    private String sortBy;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
