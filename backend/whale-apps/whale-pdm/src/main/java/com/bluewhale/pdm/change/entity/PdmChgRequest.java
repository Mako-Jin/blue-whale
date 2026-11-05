package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 变更申请（ECR）
 *
 * <p>变更原因、范围与影响对象由 PDM 保存；审批动作在 M2-5 对接 OA，
 * 本阶段保留提交/通过/驳回/关闭四类人工流转。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_chg_request")
public class PdmChgRequest extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 变更申请编号（租户内唯一） */
    private String changeNo;

    /** 变更原因 */
    private String changeReason;

    /** 变更范围 */
    private String changeScope;

    /** 影响对象 JSON */
    private String impactJson;

    /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED/CLOSED */
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
}
