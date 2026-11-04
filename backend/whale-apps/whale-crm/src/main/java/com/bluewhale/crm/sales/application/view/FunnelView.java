package com.bluewhale.crm.sales.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售漏斗视图。
 *
 * @author BlueWhale
 */
@Data
public class FunnelView {

    /** 阶段明细（按 CONTACT → PROPOSAL → NEGOTIATION → WON 顺序） */
    private List<FunnelStageView> stages;

    /** 输单数量 */
    private long lostCount;

    /** 输单金额 */
    private BigDecimal lostAmount;

    /** 在途商机总数（不含输单） */
    private long totalCount;

    /** 在途商机总金额（不含输单） */
    private BigDecimal totalAmount;
}
