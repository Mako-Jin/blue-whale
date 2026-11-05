package com.bluewhale.pdm.change.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 变更单分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmChgOrderQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 变更单编号（模糊匹配） */
    private String ecoNo;

    /** 状态 */
    private String status;

    /** 来源变更申请ID */
    private Long ecrId;

    /** 发起人 */
    private Long initiatorId;
}
