package com.bluewhale.erp.scm.application.command;

import com.bluewhale.erp.scm.domain.model.PriceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增价目命令。
 *
 * @author BlueWhale
 */
@Data
public class PriceListCreateCommand {

    /** 价格类型 */
    @NotNull
    private PriceType priceType;

    /** 往来方主键（客户或供应商） */
    @NotNull
    private Long partnerId;

    /** 物料编码 */
    @NotBlank
    private String itemCode;

    /** 单价 */
    @NotNull
    @Positive
    private BigDecimal price;

    /** 币种 */
    private String currency;

    /** 有效期起（空表示不限） */
    private LocalDate validFrom;

    /** 有效期止（空表示不限） */
    private LocalDate validTo;

    /** 计量单位 */
    private String uom;

    /** 备注 */
    private String remark;
}
