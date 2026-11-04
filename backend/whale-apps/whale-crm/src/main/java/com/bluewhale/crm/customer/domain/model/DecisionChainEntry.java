package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

/**
 * 决策链成员。
 *
 * @author BlueWhale
 */
@Data
public class DecisionChainEntry {

    /** 联系人 ID（必须属于当前客户） */
    private Long contactId;

    /** 决策角色 */
    private String role;

    /** 影响力：HIGH / MEDIUM / LOW */
    private String influence;

    /** 关系说明 */
    private String remark;
}
