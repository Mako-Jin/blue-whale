package com.bluewhale.erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * ERP 物料内网同步请求
 *
 * <p>由 whale-pdm 推送零件主档，{@code pdmPartNo} 作为幂等键。</p>
 *
 * @author BlueWhale
 */
@Data
public class ErpInternalItemUpsertRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** PDM 零件编码（幂等键） */
    @NotBlank(message = "{validation.erp.erp-internal-item-upsert.pdm-part-no-required}")
    private String pdmPartNo;

    /** 物料名称 */
    @NotBlank(message = "{validation.erp.erp-internal-item-upsert.name-required}")
    private String name;

    /** 状态：ENABLED/DISABLED，留空按 ENABLED */
    private String status;

    /** 备注 */
    private String remark;
}
