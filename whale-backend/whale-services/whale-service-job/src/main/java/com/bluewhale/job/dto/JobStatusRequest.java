package com.bluewhale.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启停定时任务请求。
 *
 * @author BlueWhale
 */
@Data
public class JobStatusRequest {

    /** 任务状态：1 运行中 / 0 已停止 */
    @NotNull(message = "{validation.job.status.status-required}")
    @Min(value = 0, message = "{validation.job.status.status-invalid}")
    @Max(value = 1, message = "{validation.job.status.status-invalid}")
    private Integer status;
}
