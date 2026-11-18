package com.bluewhale.job.vo;

import com.bluewhale.job.dto.JobTransferDependency;
import com.bluewhale.job.dto.JobTransferJob;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务导出结果：可直接作为 {@link com.bluewhale.job.dto.JobImportRequest} 的
 * {@code jobs} / {@code dependencies} 回灌。
 *
 * @author BlueWhale
 */
@Data
public class JobExportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 导出格式版本 */
    private String version;

    /** 导出时间 */
    private LocalDateTime exportedAt;

    /** 任务定义 */
    private List<JobTransferJob> jobs;

    /** 依赖边（方向：上游 → 下游） */
    private List<JobTransferDependency> dependencies;
}
