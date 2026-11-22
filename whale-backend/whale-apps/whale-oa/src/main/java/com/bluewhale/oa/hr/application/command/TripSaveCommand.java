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
    @NotBlank(message = "{validation.oa.trip.code-required}")
    @Size(max = 64, message = "{validation.oa.trip.code-size}")
    private String code;

    /** 申请人账号 */
    @NotBlank(message = "{validation.oa.trip.applicant-required}")
    @Size(max = 64, message = "{validation.oa.trip.applicant-size}")
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 出差目的地 */
    @NotBlank(message = "{validation.oa.trip.destination-required}")
    @Size(max = 128, message = "{validation.oa.trip.destination-size}")
    private String destination;

    /** 出差开始日期 */
    @NotNull(message = "{validation.oa.trip.start-date-required}")
    private LocalDate startDate;

    /** 出差结束日期 */
    @NotNull(message = "{validation.oa.trip.end-date-required}")
    private LocalDate endDate;

    /** 交通方式：TRAIN / PLANE / CAR / OTHER */
    @Pattern(regexp = "TRAIN|PLANE|CAR|OTHER", message = "{validation.oa.trip.transport-type-pattern}")
    private String transportType;

    /** 预算金额（元） */
    @DecimalMin(value = "0", message = "{validation.oa.trip.budget-amount-min}")
    private BigDecimal budgetAmount;

    /** 预支金额（元，不得超过预算） */
    @DecimalMin(value = "0", message = "{validation.oa.trip.advance-amount-min}")
    private BigDecimal advanceAmount;

    /** 出差行程 */
    @Valid
    @Size(max = 50, message = "{validation.oa.trip.itineraries-size}")
    private List<TripItineraryCommand> itineraries;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.trip.remark-size}")
    private String remark;
}
