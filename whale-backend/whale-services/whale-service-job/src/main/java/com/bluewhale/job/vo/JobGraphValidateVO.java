package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 整图环检测结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class JobGraphValidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否无环 */
    private boolean acyclic;

    /** 找到的环（首尾为同一任务ID）；无环时为空列表 */
    private List<Long> cycleJobIds;
}
