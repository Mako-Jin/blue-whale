package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更单视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变更单ID */
    private Long id;

    /** 变更单编号 */
    private String ecoNo;

    /** 来源变更申请ID */
    private Long ecrId;

    /** 变更标题 */
    private String title;

    /** 影响评估 */
    private String impactAssessment;

    /** 影响对象 */
    private List<PdmChgImpactObjectVO> impactObjects;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 变更任务 */
    private List<PdmChgTaskVO> tasks;
}
