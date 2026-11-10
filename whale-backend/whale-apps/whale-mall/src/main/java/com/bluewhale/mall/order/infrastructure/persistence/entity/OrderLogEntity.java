package com.bluewhale.mall.order.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流水实体，对应表 {@code mall_order_log}。
 *
 * <p>不继承 {@code BaseEntity}：流水只追加，不需要 {@code deleted} 语义。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("mall_order_log")
public class OrderLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 动作 */
    private String action;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 说明 */
    private String remark;

    /** 记录时间 */
    private LocalDateTime createTime;
}
