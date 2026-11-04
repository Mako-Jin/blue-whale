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
    @NotBlank(message = "产品编码不能为空")
    @Size(max = 64, message = "产品编码长度不能超过 64")
    private String code;

    /** 产品名称 */
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128, message = "产品名称长度不能超过 128")
    private String name;

    /** 零件类型：PRODUCT/ASSEMBLY/PART/RAW_MATERIAL */
    @Pattern(regexp = "PRODUCT|ASSEMBLY|PART|RAW_MATERIAL",
            message = "零件类型仅支持 PRODUCT / ASSEMBLY / PART / RAW_MATERIAL")
    private String partType;

    /** 材质 */
    @Size(max = 64, message = "材质长度不能超过 64")
    private String material;

    /** 规格 */
    @Size(max = 128, message = "规格长度不能超过 128")
    private String spec;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 物料分类编码 */
    @Size(max = 64, message = "物料分类编码长度不能超过 64")
    private String categoryCode;

    /** ERP 物料编码映射 */
    @Size(max = 64, message = "ERP 物料编码长度不能超过 64")
    private String erpItemCode;

    /** 来源类型：STANDARD/COMMON/UNIQUE */
    @Pattern(regexp = "STANDARD|COMMON|UNIQUE",
            message = "来源类型仅支持 STANDARD / COMMON / UNIQUE")
    private String sourceType;

    /** 状态：ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
