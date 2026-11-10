package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增需求预测命令。
 *
 * @author BlueWhale
 */
@Data
public class ForecastCreateCommand {

    /** 预测期间（YYYY-MM） */
    @NotBlank
    private String period;

    /** 版本号（默认 1） */
    private Integer version;

    /** 备注 */
    private String remark;

    /** 预测分录 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 预测分录入参。
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 预测数量 */
        @NotNull
        private BigDecimal quantity;

        /** 计量单位 */
        private String uom;
    }
}
