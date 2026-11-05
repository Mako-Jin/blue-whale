package com.bluewhale.pdm.change.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 变更申请分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmChgRequestQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 申请编号（模糊匹配） */
    private String changeNo;

    /** 状态：DRAFT/SUBMITTED/APPROVED/REJECTED/CLOSED */
    private String status;

    /** 发起人 */
    private Long initiatorId;
}
