package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CRP 计算结果。
 *
 * @author BlueWhale
 */
@Data
public class CrpResult {

    /** 工作中心日负荷 */
    private List<CrpLoadBucket> loads;

    /** 工序排程条带 */
    private List<CrpScheduleSlot> schedule;

    /** 因缺工艺路线或工作中心未维护而排除的订单数 */
    private int excludedOrderCount;

    /** 超出查询区间的工时 */
    private BigDecimal beyondHorizonHours;
}
