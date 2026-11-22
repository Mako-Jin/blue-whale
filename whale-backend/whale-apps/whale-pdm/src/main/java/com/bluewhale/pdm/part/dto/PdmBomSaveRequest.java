package com.bluewhale.pdm.part.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * BOM 新建请求
 *
 * @author BlueWhale
 */
@Data
public class PdmBomSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成品/部件零件ID */
    @NotNull(message = "{validation.pdm.pdm-bom.product-id-required}")
    private Long productId;

    /** BOM 编号（缺省按产品编码生成） */
    @Size(max = 64, message = "{validation.pdm.pdm-bom.bom-no-size}")
    private String bomNo;

    /** 视图：EBOM/PBOM/MBOM，缺省 EBOM */
    @Pattern(regexp = "EBOM|PBOM|MBOM", message = "{validation.pdm.pdm-bom.view-pattern}")
    private String view;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-bom.remark-size}")
    private String remark;
}
