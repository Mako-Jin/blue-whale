package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人任务实体，对应表 {@code oa_task}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_task")
public class OaTaskEntity extends BaseEntity {

    private String title;

    private String description;

    private Long ownerId;

    private String ownerName;

    private Long creatorId;

    private String creatorName;

    private String priority;

    private String status;

    private LocalDate dueDate;

    private String source;

    private Long sourceId;

    private LocalDateTime finishTime;
}
