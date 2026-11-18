package com.bluewhale.job.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DAG 拓扑图边视图对象：方向固定为上游 → 下游。
 *
 * @author BlueWhale
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobGraphEdgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上游任务ID */
    private Long from;

    /** 下游任务ID */
    private Long to;
}
