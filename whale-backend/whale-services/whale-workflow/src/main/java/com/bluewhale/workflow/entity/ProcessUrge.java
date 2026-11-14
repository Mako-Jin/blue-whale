package com.bluewhale.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程催办记录实体，对应表 {@code process_urge}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_urge")
public class ProcessUrge extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID */
    private Long instanceId;

    /** 指定催办任务ID；为空表示实例级催办 */
    private Long taskId;

    /** 催办人账号 */
    private String urger;

    /** 被催办人账号；为空表示全部待办 */
    private String targetAccount;

    /** 催办说明 */
    private String comment;

    /** 频控时间窗（创建时间截断到小时，形如 {@code yyyyMMddHH}），用于唯一键防并发绕过 */
    private String windowBucket;
}
