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
    @NotBlank(message = "关闭结论不能为空")
    @Size(max = 255, message = "关闭结论长度不能超过 255")
    private String conclusion;

    /** 关闭人 */
    @Size(max = 64, message = "关闭人长度不能超过 64")
    private String closedBy;
}
