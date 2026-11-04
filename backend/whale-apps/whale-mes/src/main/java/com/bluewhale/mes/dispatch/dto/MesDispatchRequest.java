package com.bluewhale.mes.dispatch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 派工请求：把待派工任务批量派到产线 / 班组。
 *
 * @author BlueWhale
 */
@Data
public class MesDispatchRequest {

    /** 任务 ID 列表 */
    @NotEmpty(message = "派工任务不能为空")
    private List<Long> taskIds;

    /** 派工产线 ID */
    @NotNull(message = "派工产线不能为空")
    private Long lineId;

    /** 派工班组 ID */
    private Long teamId;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
