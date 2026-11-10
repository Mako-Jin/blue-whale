package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 任务依赖关系视图对象。
 *
 * @author BlueWhale
 */
@Data
public class JobDependencyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前任务ID */
    private Long jobId;

    /** 上游任务ID列表（当前任务依赖的任务） */
    private List<Long> upstreamJobIds;

    /** 下游任务ID列表（依赖当前任务的任务） */
    private List<Long> downstreamJobIds;
}
