package com.bluewhale.video.application.command;

import lombok.Data;

/**
 * 创作者分页查询命令（运营侧）。
 *
 * @author BlueWhale
 */
@Data
public class CreatorQueryCommand {

    /** 昵称关键词 */
    private String keyword;

    /** 状态筛选：1 启用 / 0 停用，为空表示全部 */
    private Integer status;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
