package com.bluewhale.video.application.command;

import lombok.Data;

/**
 * 系列分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class SeriesQueryCommand {

    /** 标题关键词 */
    private String keyword;

    /** 内容类型 */
    private String contentType;

    /** 发布状态 */
    private String publishState;

    /** 分类 ID */
    private Long categoryId;

    /** 创作者 ID */
    private Long creatorId;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
