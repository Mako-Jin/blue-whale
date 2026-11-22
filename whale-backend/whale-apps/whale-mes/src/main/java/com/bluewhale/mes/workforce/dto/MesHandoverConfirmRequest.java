package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 交接班确认请求
 *
 * @author BlueWhale
 */
@Data
public class MesHandoverConfirmRequest {

    /** 确认人 */
    @NotBlank(message = "{validation.mes.mes-handover-confirm.confirmed-by-required}")
    @Size(max = 64, message = "{validation.mes.mes-handover-confirm.confirmed-by-size}")
    private String confirmedBy;
}
