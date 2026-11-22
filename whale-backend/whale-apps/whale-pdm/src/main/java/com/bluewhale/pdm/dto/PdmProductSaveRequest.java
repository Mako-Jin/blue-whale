package com.bluewhale.pdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 产品新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class PdmProductSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品编码（租户内唯一） */
    @NotBlank(message = "{validation.pdm.pdm-product.code-required}")
    @Size(max = 64, message = "{validation.pdm.pdm-product.code-size}")
    private String code;

    /** 产品名称 */
    @NotBlank(message = "{validation.pdm.pdm-product.name-required}")
    @Size(max = 128, message = "{validation.pdm.pdm-product.name-size}")
    private String name;

    /** 零件类型：PRODUCT/ASSEMBLY/PART/RAW_MATERIAL */
    @Pattern(regexp = "PRODUCT|ASSEMBLY|PART|RAW_MATERIAL",
            message = "{validation.pdm.pdm-product.part-type-pattern}")
    private String partType;

    /** 材质 */
    @Size(max = 64, message = "{validation.pdm.pdm-product.material-size}")
    private String material;

    /** 规格 */
    @Size(max = 128, message = "{validation.pdm.pdm-product.spec-size}")
    private String spec;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.pdm.pdm-product.uom-size}")
    private String uom;

    /** 物料分类编码 */
    @Size(max = 64, message = "{validation.pdm.pdm-product.category-code-size}")
    private String categoryCode;

    /** ERP 物料编码映射 */
    @Size(max = 64, message = "{validation.pdm.pdm-product.erp-item-code-size}")
    private String erpItemCode;

    /** 来源类型：STANDARD/COMMON/UNIQUE */
    @Pattern(regexp = "STANDARD|COMMON|UNIQUE",
            message = "{validation.pdm.pdm-product.source-type-pattern}")
    private String sourceType;

    /** 状态：ENABLED / DISABLED */
    @NotBlank(message = "{validation.pdm.pdm-product.status-required}")
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.pdm.pdm-product.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-product.remark-size}")
    private String remark;
}
