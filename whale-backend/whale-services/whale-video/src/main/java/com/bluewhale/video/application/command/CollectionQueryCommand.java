package com.bluewhale.video.application.command;

import lombok.Data;

/**
 * 电影片单分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class CollectionQueryCommand {

    /** 标题关键词 */
    private String keyword;

    /** 发布状态（仅运营可用，用户侧强制已上架） */
    private String publishState;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
