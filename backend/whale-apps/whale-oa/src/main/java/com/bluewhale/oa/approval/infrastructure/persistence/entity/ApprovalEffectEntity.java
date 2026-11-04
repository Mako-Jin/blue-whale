package com.bluewhale.oa.approval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批业务副作用实体，对应表 {@code oa_approval_effect}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_effect")
public class ApprovalEffectEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 副作用类型 */
    private String effectType;

    /** 执行状态 */
    private String effectStatus;

    /** 执行参数快照（JSON） */
    private String payload;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMsg;
}
