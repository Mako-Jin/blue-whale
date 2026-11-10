package com.bluewhale.oa.approval.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批配置分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalConfigQuery extends PageQuery {

    /** 业务类型（模糊匹配） */
    private String businessType;

    /** 是否启用 */
    private Boolean enabled;
}
