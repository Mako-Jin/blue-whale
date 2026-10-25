package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单个已关闭迭代的速率数据
 *
 * @author BlueWhale
 */
@Data
public class VelocitySprintVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 迭代ID */
    private String sprintId;

    /** 迭代名称 */
    private String sprintName;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 承诺故事点 */
    private BigDecimal committedPoints;

    /** 完成故事点 */
    private BigDecimal completedPoints;
}
