package com.bluewhale.mall.order.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流水（只追加）。
 *
 * @author BlueWhale
 */
@Data
public class OrderLog {

    /** 动作：CREATE / PAY / CANCEL / SHIP / COMPLETE */
    private String action;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作人 ID（系统动作可空） */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 说明 */
    private String remark;

    /** 记录时间 */
    private LocalDateTime createTime;
}
