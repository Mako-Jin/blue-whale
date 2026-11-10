package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WMS 入库结果视图。
 *
 * @author BlueWhale
 */
@Data
public class StockInboundView {

    /** 入库单号 */
    private String docNo;

    /** 来源系统 */
    private String source;

    /** 是否命中幂等（重复推送同一单号时为 true，库存不再累加） */
    private boolean duplicated;

    /** 本次实际入库总数量（幂等命中时为首次入库的总量） */
    private Integer totalQuantity;

    /** 入库时间 */
    private LocalDateTime createTime;
}
