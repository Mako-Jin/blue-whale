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
    @NotBlank(message = "确认人不能为空")
    @Size(max = 64, message = "确认人长度不能超过 64")
    private String confirmedBy;
}
