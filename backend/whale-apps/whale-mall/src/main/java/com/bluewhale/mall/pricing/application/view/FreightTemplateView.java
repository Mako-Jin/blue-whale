package com.bluewhale.mall.pricing.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 运费模板视图。
 *
 * @author BlueWhale
 */
@Data
public class FreightTemplateView {

    /** 模板 ID */
    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 计费方式 */
    private String chargeType;

    /** 固定运费金额 */
    private BigDecimal baseAmount;

    /** 包邮门槛 */
    private BigDecimal freeThreshold;

    /** 每件运费（仅 BY_PIECE） */
    private BigDecimal piecePrice;

    /** 包邮件数门槛（仅 BY_PIECE 可选） */
    private Integer pieceFreeThreshold;

    /** 首重重量 kg（仅 BY_WEIGHT） */
    private BigDecimal firstWeightKg;

    /** 续重步长 kg（仅 BY_WEIGHT） */
    private BigDecimal continuedWeightKg;

    /** 续重每步运费（仅 BY_WEIGHT） */
    private BigDecimal continuedAmount;

    /** 区域费率（仅 REGIONAL） */
    private List<RegionRateView> regionRates;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 区域费率视图。
     */
    @Data
    public static class RegionRateView {

        /** 主键 */
        private Long id;

        /** 区域 */
        private String region;

        /** 该区域运费 */
        private BigDecimal amount;

        /** 该区域满额包邮门槛（可空） */
        private BigDecimal freeThreshold;
    }
}
