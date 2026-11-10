package com.bluewhale.erp.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 薪资计算参数配置。
 *
 * <p>社保/公积金比例、个税起征点、加班倍数与月计薪天数可随政策调整，
 * 改配置即可生效，无需改动领域代码。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.hr.payroll")
public class HrPayrollProperties {

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
