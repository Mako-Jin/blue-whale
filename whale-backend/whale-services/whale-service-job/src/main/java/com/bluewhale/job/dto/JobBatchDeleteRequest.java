package com.bluewhale.job.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求。
 *
 * <p>删除为逻辑删除，语义与单条 {@code DELETE /job/v1/info/{id}} 完全一致。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobBatchDeleteRequest {

    /** 任务ID列表 */
    @NotEmpty(message = "任务ID列表不能为空")
    @Size(max = 200, message = "单次批量操作不能超过 200 条")
    private List<Long> jobIds;
}
