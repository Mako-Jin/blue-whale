package com.bluewhale.mes.dispatch.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 排程计划动作请求（锁定 / 取消）。
 *
 * @author BlueWhale
 */
@Data
public class MesScheduleActionRequest {

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-schedule-action.remark-size}")
    private String remark;
}
