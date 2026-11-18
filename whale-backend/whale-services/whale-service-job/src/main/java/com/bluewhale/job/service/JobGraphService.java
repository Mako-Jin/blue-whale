package com.bluewhale.job.service;

import com.bluewhale.job.vo.JobGraphValidateVO;
import com.bluewhale.job.vo.JobGraphVO;

/**
 * 任务依赖拓扑图服务（只读）。
 *
 * @author BlueWhale
 */
public interface JobGraphService {

    /**
     * 查询任务依赖拓扑图。
     *
     * @param jobGroup 任务分组；为空时返回全量
     * @return 节点与依赖边（方向：上游 → 下游）
     */
    JobGraphVO graph(String jobGroup);

    /**
     * 全量节点环检测。
     *
     * @return 检测结果；有环时返回闭环路径
     */
    JobGraphValidateVO validate();
}
