package com.bluewhale.music.application.command;

import lombok.Data;

/**
 * 申诉复核分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class AppealQueryCommand {

    /** 歌曲 ID，可选 */
    private Long songId;

    /** 申诉状态：PENDING / APPROVED / REJECTED，可选 */
    private String status;

    /** 页码 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
