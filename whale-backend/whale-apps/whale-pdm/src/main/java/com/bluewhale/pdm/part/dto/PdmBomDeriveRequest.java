package com.bluewhale.pdm.part.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * BOM 派生视图请求
 *
 * @author BlueWhale
 */
@Data
public class PdmBomDeriveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标视图：PBOM/MBOM */
    @NotBlank(message = "{validation.pdm.pdm-bom-derive.view-required}")
    @Pattern(regexp = "PBOM|MBOM", message = "{validation.pdm.pdm-bom-derive.view-pattern}")
    private String view;

    /** 派生 BOM 编号（缺省按源编号 + 视图生成） */
    @Size(max = 64, message = "{validation.pdm.pdm-bom-derive.bom-no-size}")
    private String bomNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-bom-derive.remark-size}")
    private String remark;
}
