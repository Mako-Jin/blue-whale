package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文化动态实体，对应表 {@code oa_culture_event}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_culture_event")
public class CultureEventEntity extends BaseEntity {

    private String eventType;

    private String title;

    private String content;

    private LocalDate eventDate;

    private Long coverFileId;

    private String status;

    private Long publisherId;

    private String publisherName;

    private LocalDateTime publishTime;

    private String remark;
}
