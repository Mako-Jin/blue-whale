package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告实体，对应表 {@code oa_notice}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_notice")
public class NoticeEntity extends BaseEntity {

    private String title;

    private String content;

    private String scopeType;

    private String scopeValue;

    private String status;

    private String publisher;

    private Long publisherId;

    private LocalDateTime publishTime;

    private String remark;
}
