package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同流程关联实体，对应表 {@code ct_contract_flow}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract_flow")
public class CtContractFlow extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 合同版本ID */
    private Long versionId;

    /** 工作流实例ID */
    private Long processInstanceId;

    /** 流程定义ID */
    private Long definitionId;

    /** 阶段：APPROVAL 审批 / SIGN 签署 */
    private String stage;

    /** 状态：RUNNING/APPROVED/REJECTED/CANCELLED */
    private String status;
}
