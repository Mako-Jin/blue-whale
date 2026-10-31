package com.bluewhale.music.application.command;

import lombok.Data;

/**
 * 站内搜索命令。
 *
 * @author BlueWhale
 */
@Data
public class SearchCommand {

    /** 关键词；为空时返回稳定的空结果 */
    private String keyword;

    /** 搜索类型：ALL / SONG / ARTIST / ALBUM / PLAYLIST，缺省 ALL */
    private String type = "ALL";
}
