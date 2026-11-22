package com.bluewhale.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class ErpItemSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料编码（租户内唯一） */
    @NotBlank(message = "{validation.erp.erp-item.code-required}")
    @Size(max = 64, message = "{validation.erp.erp-item.code-size}")
    private String code;

    /** 物料名称 */
    @NotBlank(message = "{validation.erp.erp-item.name-required}")
    @Size(max = 128, message = "{validation.erp.erp-item.name-size}")
    private String name;

    /** 状态：ENABLED / DISABLED */
    @NotBlank(message = "{validation.erp.erp-item.status-required}")
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.erp.erp-item.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.erp.erp-item.remark-size}")
    private String remark;
}
