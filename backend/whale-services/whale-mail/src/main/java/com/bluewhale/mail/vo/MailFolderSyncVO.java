package com.bluewhale.mail.vo;

import lombok.Data;

/**
 * 文件夹与服务器对齐的结果统计。
 *
 * <p>只回报数量，不回传整张列表——调用方（前端）在刷新后本就会重新拉一次
 * {@code /mail/folder/list}，再传一份只会让两处数据有先后不一致的机会。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailFolderSyncVO {

    /** 新发现并接入的文件夹数 */
    private int added;

    /** 名称或 IMAP 映射被纠正的文件夹数 */
    private int updated;

    /** 服务器上已消失、本地已下线的文件夹数 */
    private int removed;

    /** 服务器可存邮件的文件夹总数 */
    private int remote;

    /** 对齐后的本地文件夹总数 */
    private int total;
}
