package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户 360 中的商机摘要（只读，不复制商机主数据）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerOpportunitySummary {

    /** 商机总数 */
    private long totalCount;

    /** 商机总金额 */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 商机明细 */
    private List<Item> items = new ArrayList<>();

    /**
     * 商机摘要项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Item {

        /** 商机 ID */
        private Long id;

        /** 商机名称 */
        private String name;

        /** 阶段 */
        private String stage;

        /** 金额 */
        private BigDecimal amount;
    }
}
