package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增生产订单命令。
 *
 * @author BlueWhale
 */
@Data
public class ProductionOrderCreateCommand {

    /** 产出物料编码 */
    @NotBlank
    private String itemCode;

    /** 计划生产数量 */
    @NotNull
    private BigDecimal plannedQuantity;

    /** 领料仓库 */
    @NotNull
    private Long issueWarehouseId;

    /** 完工入库仓库 */
    @NotNull
    private Long targetWarehouseId;

    /** 计划开工日期 */
    @NotNull
    private LocalDate planStartDate;

    /** 计划完工日期 */
    private LocalDate planEndDate;

    /** 备注 */
    private String remark;

    /** 用料 */
    @Valid
    @NotEmpty
    private List<Material> materials;

    /**
     * 用料入参。
     */
    @Data
    public static class Material {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 需求数量 */
        @NotNull
        private BigDecimal requiredQuantity;

        /** 计量单位 */
        private String uom;

        /** 是否倒冲领料 */
        private Boolean backflush;
    }
}
