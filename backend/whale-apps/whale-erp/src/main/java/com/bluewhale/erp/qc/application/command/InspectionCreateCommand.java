package com.bluewhale.erp.qc.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增检验单命令。
 *
 * @author BlueWhale
 */
@Data
public class InspectionCreateCommand {

    /** 检验类型：IQC / IPQC / FQC */
    @NotBlank
    private String inspectionType;

    /** 来源类型：PURCHASE_RECEIPT / PRODUCTION_ORDER / MANUAL */
    private String sourceType;

    /** 来源单据号 */
    @NotBlank
    private String sourceNo;

    /** 物料编码 */
    @NotBlank
    private String itemCode;

    /** 批次号 */
    private String lotNo;

    /** 送检数量 */
    @NotNull
    private BigDecimal quantity;

    /** 供应商编码（IQC） */
    private String supplierCode;

    /** 检验员 */
    private String inspector;

    /** 检验日期 */
    @NotNull
    private LocalDate inspectDate;

    /** 量具编号 */
    private String toolNo;

    /** 备注 */
    private String remark;

    /** 检验项 */
    @Valid
    @NotEmpty
    private List<Item> items;

    /**
     * 检验项入参。
     */
    @Data
    public static class Item {

        /** 检验项名称 */
        @NotBlank
        private String itemName;

        /** 标准值/规格 */
        private String standardValue;
    }
}
