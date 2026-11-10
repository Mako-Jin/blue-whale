package com.bluewhale.job.service;

import com.bluewhale.job.dto.JobDependencySetRequest;
import com.bluewhale.job.vo.JobDependencyVO;

/**
 * 任务依赖关系服务。
 *
 * @author BlueWhale
 */
public interface JobDependencyService {

    /**
     * 查询指定任务的上游与下游依赖。
     *
     * @param jobId 任务ID
     * @return 依赖关系视图对象
     */
    JobDependencyVO getDependencies(Long jobId);

    /**
     * 全量替换指定任务的上游依赖，并同步重新调度该任务。
     *
     * @param jobId 任务ID
     * @param request 依赖配置请求
     * @return 替换后的依赖关系视图对象
     */
    JobDependencyVO saveDependencies(Long jobId, JobDependencySetRequest request);
}
