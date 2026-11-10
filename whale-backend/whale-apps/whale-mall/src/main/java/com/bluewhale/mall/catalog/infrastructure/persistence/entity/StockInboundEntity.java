package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存入库单头实体，对应表 {@code mall_stock_inbound}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_stock_inbound")
public class StockInboundEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 入库单号（租户内唯一，幂等键） */
    private String docNo;

    /** 来源系统：WMS / ERP / MANUAL */
    private String source;

    /** 明细行数 */
    private Integer lineCount;

    /** 备注 */
    private String remark;
}
