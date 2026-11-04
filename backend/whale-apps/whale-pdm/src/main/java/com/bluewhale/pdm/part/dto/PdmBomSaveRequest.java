package com.bluewhale.pdm.part.dto;

import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "产品不能为空")
    private Long productId;

    /** BOM 编号（缺省按产品编码生成） */
    @Size(max = 64, message = "BOM 编号长度不能超过 64")
    private String bomNo;

    /** 视图，M1A 仅支持 EBOM */
    private String view;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
