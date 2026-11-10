package com.bluewhale.mall.pricing.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 运费模板保存命令。
 *
 * @author BlueWhale
 */
@Data
public class FreightTemplateSaveCommand {

    /** 模板名称（租户内唯一） */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 64, message = "模板名称不得超过 64 字符")
    private String templateName;

    /** 计费方式：FREE / FIXED / THRESHOLD / BY_PIECE */
    @NotBlank(message = "必须指定计费方式")
    private String chargeType;

    /** 固定运费金额（FREE/BY_PIECE 时忽略） */
    private BigDecimal baseAmount;

    /** 包邮门槛（仅 THRESHOLD 必填） */
    private BigDecimal freeThreshold;

    /** 每件运费（仅 BY_PIECE 必填且大于 0） */
    private BigDecimal piecePrice;

    /** 包邮件数门槛（仅 BY_PIECE 可选，达到则免运费） */
    private Integer pieceFreeThreshold;

    /** 首重重量 kg（仅 BY_WEIGHT 必填且大于 0，首重费收 baseAmount） */
    private BigDecimal firstWeightKg;

    /** 续重步长 kg（仅 BY_WEIGHT 可选，不配则超首重不加价） */
    private BigDecimal continuedWeightKg;

    /** 续重每步运费（仅 BY_WEIGHT 配置续重步长时必填） */
    private BigDecimal continuedAmount;

    /** 区域费率（仅 REGIONAL 用；未命中区域回落 baseAmount） */
    private List<RegionRateCommand> regionRates;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;

    /**
     * 区域费率命令。
     */
    @Data
    public static class RegionRateCommand {

        /** 区域（省/直辖市名，模板内唯一） */
        private String region;

        /** 该区域运费 */
        private BigDecimal amount;

        /** 该区域满额包邮门槛（可空） */
        private BigDecimal freeThreshold;
    }
}
