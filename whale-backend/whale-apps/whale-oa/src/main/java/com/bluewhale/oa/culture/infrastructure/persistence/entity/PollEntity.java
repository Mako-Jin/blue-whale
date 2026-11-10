package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投票实体，对应表 {@code oa_poll}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_poll")
public class PollEntity extends BaseEntity {

    private String title;

    private String description;

    private Integer multipleChoice;

    private Integer anonymous;

    private Integer maxChoices;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private Long creatorId;

    private String creatorName;
}
