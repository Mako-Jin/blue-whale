package com.bluewhale.erp.hr.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资计算参数（社保/公积金比例、个税起征点、加班倍数与月计薪天数）。
 *
 * <p>由应用服务从配置装配，领域层只消费数值，便于单测注入不同参数。</p>
 *
 * @author BlueWhale
 */
@Data
public class HrPayrollRates {

    /** 社保个人比例（默认 10.5%） */
    private BigDecimal socialInsuranceRate = new BigDecimal("0.105");

    /** 公积金个人比例（默认 7%） */
    private BigDecimal housingFundRate = new BigDecimal("0.07");

    /** 个税起征点（默认 5000） */
    private BigDecimal taxThreshold = new BigDecimal("5000");

    /** 加班工资倍数（默认 1.5） */
    private BigDecimal overtimeMultiplier = new BigDecimal("1.5");

    /** 月计薪天数（默认 21.75） */
    private BigDecimal monthlyWorkDays = new BigDecimal("21.75");

    /** 日工作小时（默认 8） */
    private BigDecimal dailyHours = new BigDecimal("8");
}
