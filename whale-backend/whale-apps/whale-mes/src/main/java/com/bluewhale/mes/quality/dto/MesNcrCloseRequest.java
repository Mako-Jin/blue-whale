package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * NCR 关闭请求
 *
 * @author BlueWhale
 */
@Data
public class MesNcrCloseRequest {

    /** 关闭结论 */
    @NotBlank(message = "{validation.mes.mes-ncr-close.conclusion-required}")
    @Size(max = 255, message = "{validation.mes.mes-ncr-close.conclusion-size}")
    private String conclusion;

    /** 关闭人 */
    @Size(max = 64, message = "{validation.mes.mes-ncr-close.closed-by-size}")
    private String closedBy;
}
