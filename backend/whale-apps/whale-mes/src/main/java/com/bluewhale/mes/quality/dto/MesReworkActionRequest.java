package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 返工单执行动作请求
 *
 * @author BlueWhale
 */
@Data
public class MesReworkActionRequest {

    /** 返工人 */
    @Size(max = 64, message = "返工人长度不能超过 64")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
