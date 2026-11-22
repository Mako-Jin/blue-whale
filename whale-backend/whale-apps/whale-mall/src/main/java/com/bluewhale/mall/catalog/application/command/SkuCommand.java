package com.bluewhale.mall.catalog.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * SKU 命令。
 *
 * @author BlueWhale
 */
@Data
public class SkuCommand {

    /** SKU ID（编辑已有规格时必传；新增留空） */
    private Long id;

    /** SKU 编码（租户内唯一） */
    @NotBlank(message = "{validation.mall.sku.sku-code-required}")
    @Size(max = 64, message = "{validation.mall.sku.sku-code-size}")
    private String skuCode;

    /** 规格描述 JSON */
    @Size(max = 512, message = "{validation.mall.sku.spec-json-size}")
    private String specJson;

    /** SKU 图 */
    @Size(max = 512, message = "{validation.mall.sku.image-url-size}")
    private String imageUrl;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 单件重量（千克，按重运费计价用；可空） */
    @DecimalMin(value = "0.001", message = "{validation.mall.sku.weight-kg-min}")
    @Digits(integer = 8, fraction = 3, message = "{validation.mall.sku.weight-kg-digits}")
    private BigDecimal weightKg;

    /** 排序号 */
    private Integer sortNo;

    /** 价目列表（至少一项） */
    @Valid
    private List<PriceCommand> prices = new ArrayList<>();
}
