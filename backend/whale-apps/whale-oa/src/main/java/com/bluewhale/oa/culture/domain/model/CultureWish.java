package com.bluewhale.oa.culture.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生日 / 入职周年祝福。
 *
 * @author BlueWhale
 */
@Data
public class CultureWish {

    /** 主键ID */
    private Long id;

    /** 祝福类型 */
    private String wishType;

    /** 祝福对象用户ID */
    private Long targetUserId;

    /** 祝福对象姓名快照 */
    private String targetName;

    /** 祝福内容 */
    private String content;

    /** 发送人用户ID */
    private Long senderId;

    /** 发送人姓名快照 */
    private String senderName;

    /** 祝福日期 */
    private LocalDate wishDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
