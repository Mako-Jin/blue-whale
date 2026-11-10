package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户负责人变更留痕（append-only）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerOwnerLog {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 动作：CREATE / CLAIM / RELEASE / ASSIGN / RECYCLE / MERGE / IMPORT */
    private String action;

    /** 原负责人 */
    private Long fromOwnerId;

    /** 新负责人 */
    private Long toOwnerId;

    /** 操作人 */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
