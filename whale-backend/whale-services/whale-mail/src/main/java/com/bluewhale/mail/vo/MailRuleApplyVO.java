package com.bluewhale.mail.vo;

import lombok.Data;

/**
 * 规则回填历史邮件的执行结果。
 *
 * @author BlueWhale
 */
@Data
public class MailRuleApplyVO {

    /** 扫描的收件箱邮件数 */
    private Integer scanned;

    /** 被规则改写的邮件数（归类或标记发生变化） */
    private Integer changed;

    /** 是否因达到单次上限而提前结束 */
    private Boolean truncated;
}
