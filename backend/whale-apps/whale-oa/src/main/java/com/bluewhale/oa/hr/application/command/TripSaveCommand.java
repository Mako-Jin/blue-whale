package com.bluewhale.oa.hr.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 出差申请新增 / 更新命令。
 *
 * @author BlueWhale
 */
@Data
public class TripSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请单号（租户内唯一） */
    @NotBlank(message = "申请单号不能为空")
    @Size(max = 64, message = "申请单号长度不能超过 64")
    private String code;

    /** 申请人账号 */
    @NotBlank(message = "申请人不能为空")
    @Size(max = 64, message = "申请人长度不能超过 64")
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 出差目的地 */
    @NotBlank(message = "出差目的地不能为空")
    @Size(max = 128, message = "出差目的地长度不能超过 128")
    private String destination;

    /** 出差开始日期 */
    @NotNull(message = "出差开始日期不能为空")
    private LocalDate startDate;

    /** 出差结束日期 */
    @NotNull(message = "出差结束日期不能为空")
    private LocalDate endDate;

    /** 交通方式：TRAIN / PLANE / CAR / OTHER */
    @Pattern(regexp = "TRAIN|PLANE|CAR|OTHER", message = "交通方式仅支持 TRAIN / PLANE / CAR / OTHER")
    private String transportType;

    /** 预算金额（元） */
    @DecimalMin(value = "0", message = "预算金额不能为负数")
    private BigDecimal budgetAmount;

    /** 预支金额（元，不得超过预算） */
    @DecimalMin(value = "0", message = "预支金额不能为负数")
    private BigDecimal advanceAmount;

    /** 出差行程 */
    @Valid
    @Size(max = 50, message = "行程明细最多 50 条")
    private List<TripItineraryCommand> itineraries;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
