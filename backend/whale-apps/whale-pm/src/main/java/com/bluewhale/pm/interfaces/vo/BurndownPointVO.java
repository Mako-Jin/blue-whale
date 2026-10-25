package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 燃尽图单日数据点
 *
 * @author BlueWhale
 */
@Data
public class BurndownPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计日期 */
    private LocalDate date;

    /** 剩余故事点 */
    private BigDecimal remainingPoints;

    /** 理想剩余故事点 */
    private BigDecimal idealPoints;
}
