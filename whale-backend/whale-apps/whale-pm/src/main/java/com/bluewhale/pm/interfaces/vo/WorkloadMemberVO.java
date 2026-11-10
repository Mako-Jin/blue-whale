package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 成员负载单行数据
 *
 * @author BlueWhale
 */
@Data
public class WorkloadMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 负责人用户ID（未指派时为空） */
    private String assigneeId;

    /** 工作项总数 */
    private Integer totalCount;

    /** 未完成工作项数 */
    private Integer openCount;

    /** 已完成工作项数 */
    private Integer doneCount;

    /** 未完成故事点 */
    private BigDecimal openPoints;

    /** 已完成故事点 */
    private BigDecimal donePoints;
}
