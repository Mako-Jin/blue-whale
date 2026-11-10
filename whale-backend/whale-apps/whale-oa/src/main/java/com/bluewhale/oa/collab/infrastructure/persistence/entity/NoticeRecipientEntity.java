package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告接收人实体，对应表 {@code oa_notice_recipient}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_notice_recipient")
public class NoticeRecipientEntity extends BaseEntity {

    private Long noticeId;

    private Long userId;

    private String userName;

    private Integer readFlag;

    private LocalDateTime readTime;
}
