package com.bluewhale.mail.vo;

import lombok.Data;

/**
 * 邮箱文件夹视图。
 *
 * @author BlueWhale
 */
@Data
public class MailFolderVO {

    /** 文件夹ID */
    private Long id;

    /** 文件夹编码 */
    private String folderCode;

    /** 展示名称 */
    private String name;

    /** 类型：SYSTEM / CUSTOM */
    private String folderType;

    /** 未读数量 */
    private Integer unreadCount;

    /** 邮件总数 */
    private Integer totalCount;
}
