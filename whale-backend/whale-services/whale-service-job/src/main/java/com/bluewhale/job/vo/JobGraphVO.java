package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * DAG 拓扑图视图对象：全量节点与依赖边。
 *
 * @author BlueWhale
 */
@Data
public class JobGraphVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点列表（按任务ID升序） */
    private List<JobGraphNodeVO> nodes;

    /** 依赖边列表（方向：上游 → 下游，按 from、to 升序） */
    private List<JobGraphEdgeVO> edges;
}
