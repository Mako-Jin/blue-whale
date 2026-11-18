package com.bluewhale.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量启停请求。
 *
 * <p>{@code jobIds} 允许含重复项（服务层去重），上限 200 条，避免长时间占用 Web 线程。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobBatchStatusRequest {

    /** 任务ID列表 */
    @NotEmpty(message = "任务ID列表不能为空")
    @Size(max = 200, message = "单次批量操作不能超过 200 条")
    private List<Long> jobIds;

    /** 目标状态：1 运行中 / 0 已停止 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
