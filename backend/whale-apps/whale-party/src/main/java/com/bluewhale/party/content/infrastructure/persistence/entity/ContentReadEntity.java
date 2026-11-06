package com.bluewhale.party.content.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 党建内容阅读记录实体，对应表 {@code pty_content_read}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_content_read")
public class ContentReadEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 内容 ID */
    private Long contentId;

    /** 阅读人用户 ID */
    private Long userId;

    /** 阅读人姓名 */
    private String userName;

    /** 最近阅读时间 */
    private LocalDateTime readAt;
}
