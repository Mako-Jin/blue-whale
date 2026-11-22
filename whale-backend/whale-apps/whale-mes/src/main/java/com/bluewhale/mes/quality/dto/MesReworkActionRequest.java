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
    @Size(max = 64, message = "{validation.mes.mes-rework-action.operator-size}")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-rework-action.remark-size}")
    private String remark;
}
