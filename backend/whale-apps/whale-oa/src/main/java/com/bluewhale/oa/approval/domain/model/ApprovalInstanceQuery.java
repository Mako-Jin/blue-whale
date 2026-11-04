package com.bluewhale.oa.approval.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批实例分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalInstanceQuery extends PageQuery {

    /** 业务类型 */
    private String businessType;

    /** 审批状态 */
    private String status;

    /** 发起人用户ID */
    private Long initiatorId;

    /** 业务单据ID */
    private Long businessId;

    /** 关键字（标题或业务单号） */
    private String keyword;
}
