package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商务订单下发日志实体，对应表 {@code crm_order_push_log}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_order_push_log")
public class OrderPushLogEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 尝试序号 */
    private Integer attemptNo;

    /** 请求引用 */
    private String requestRef;

    /** 响应引用 */
    private String responseRef;

    /** 结果状态 */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costMs;
}
