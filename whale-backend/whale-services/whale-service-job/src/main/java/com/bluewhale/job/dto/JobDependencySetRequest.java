package com.bluewhale.job.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务依赖配置请求：以下游任务视角全量替换其上游任务列表。
 *
 * @author BlueWhale
 */
@Data
public class JobDependencySetRequest {

    /** 上游任务ID列表（全量替换；为空表示清除全部上游依赖） */
    private List<Long> upstreamJobIds;
}
