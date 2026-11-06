package com.bluewhale.mail.vo;

import lombok.Data;

import java.util.Map;

/**
 * 未读数视图。
 *
 * @author BlueWhale
 */
@Data
public class MailUnreadVO {

    /** 全部文件夹未读总数 */
    private long total;

    /** 按文件夹编码分组的未读数 */
    private Map<String, Long> byFolder;
}
