package com.bluewhale.job.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务导入请求。
 *
 * <p>任务与依赖分别逐条处理，单条失败只记入失败明细，不影响其余条目。
 * 条目校验在服务层逐条执行，避免一条坏数据让整份文件被 400 拒绝。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobImportRequest {

    /** 冲突策略：SKIP（默认，已存在则跳过）/ OVERWRITE（已存在则覆盖） */
    private String conflictStrategy;

    /** 待导入任务定义 */
    private List<JobTransferJob> jobs;

    /** 待导入依赖边（在任务全部落库后应用） */
    private List<JobTransferDependency> dependencies;
}
