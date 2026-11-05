package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 变更单（ECO）
 *
 * <p>由已通过 ECR 创建，保存影响评估、执行计划与影响对象快照；
 * 审批通过前不可执行，执行只登记任务结果版本号，不覆盖已发布版本。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_chg_order")
public class PdmChgOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 变更单编号（租户内唯一） */
    private String ecoNo;

    /** 来源变更申请ID */
    private Long ecrId;

    /** 变更标题 */
    private String title;

    /** 影响评估 */
    private String impactAssessment;

    /** 影响对象快照 JSON */
    private String impactJson;

    /** 执行计划 */
    private String executePlan;

    /** 状态 */
    private String status;

    /** 发起人 */
    private Long initiatorId;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 审批人 */
    private Long approveBy;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 审批意见 */
    private String approveComment;

    /** 执行开始时间 */
    private LocalDateTime executeTime;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** OA 审批实例ID */
    private Long approvalInstanceId;

    /** OA 审批状态：RUNNING/APPROVED/REJECTED/WITHDRAWN */
    private String approvalStatus;

    /** OA 状态同步时间 */
    private LocalDateTime approvalSyncTime;

    /** 审批来源：OA/MANUAL */
    private String approvalSource;
}
