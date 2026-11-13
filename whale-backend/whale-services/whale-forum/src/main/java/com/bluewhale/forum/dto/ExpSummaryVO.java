package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 论坛经验汇总（总额 + 最近流水）。
 *
 * @author BlueWhale
 */
@Data
public class ExpSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 累计经验 */
    private long total;

    /** 最近流水 */
    private List<ExpItemVO> recent;
}
