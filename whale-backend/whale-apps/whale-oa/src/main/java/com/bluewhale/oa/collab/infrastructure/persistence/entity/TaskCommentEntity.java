package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务评论实体，对应表 {@code oa_task_comment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_task_comment")
public class TaskCommentEntity extends BaseEntity {

    private Long taskId;

    private Long userId;

    private String userName;

    private String content;
}
