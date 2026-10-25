package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 累积流图单条状态序列
 *
 * @author BlueWhale
 */
@Data
public class CumulativeFlowSeriesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态分类：TODO / IN_PROGRESS / DONE */
    private String category;

    /** 与日期轴一一对应的累计数量 */
    private List<Integer> values;
}
