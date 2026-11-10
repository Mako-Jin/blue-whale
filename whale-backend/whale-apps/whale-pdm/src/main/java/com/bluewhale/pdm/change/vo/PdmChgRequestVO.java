package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更申请视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgRequestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请ID */
    private Long id;

    /** 申请编号 */
    private String changeNo;

    /** 变更原因 */
    private String changeReason;

    /** 变更范围 */
    private String changeScope;

    /** 影响对象 */
    private List<PdmChgImpactObjectVO> impactObjects;

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

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** OA 审批实例ID */
    private Long approvalInstanceId;

    /** OA 审批状态 */
    private String approvalStatus;

    /** OA 状态同步时间 */
    private LocalDateTime approvalSyncTime;

    /** 审批来源：OA/MANUAL */
    private String approvalSource;
}
