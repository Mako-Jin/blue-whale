package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 迭代燃尽图（按天剩余故事点与理想线）
 *
 * @author BlueWhale
 */
@Data
public class BurndownVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 迭代ID（无进行中迭代时为空） */
    private String sprintId;

    /** 迭代名称 */
    private String sprintName;

    /** 迭代开始日期 */
    private LocalDate startDate;

    /** 迭代结束日期 */
    private LocalDate endDate;

    /** 迭代承诺故事点合计 */
    private BigDecimal totalPoints;

    /** 每日数据点 */
    private List<BurndownPointVO> points;
}
