package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 出差行程命令。
 *
 * @author BlueWhale
 */
@Data
public class TripItineraryCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行程序号（从 1 开始） */
    @NotNull(message = "{validation.oa.trip-itinerary.day-no-required}")
    @Min(value = 1, message = "{validation.oa.trip-itinerary.day-no-min}")
    private Integer dayNo;

    /** 行程日期 */
    @NotNull(message = "{validation.oa.trip-itinerary.trip-date-required}")
    private LocalDate tripDate;

    /** 出发地 */
    @NotBlank(message = "{validation.oa.trip-itinerary.from-place-required}")
    @Size(max = 128, message = "{validation.oa.trip-itinerary.from-place-size}")
    private String fromPlace;

    /** 到达地 */
    @NotBlank(message = "{validation.oa.trip-itinerary.to-place-required}")
    @Size(max = 128, message = "{validation.oa.trip-itinerary.to-place-size}")
    private String toPlace;

    /** 交通方式 */
    @Pattern(regexp = "TRAIN|PLANE|CAR|OTHER", message = "{validation.oa.trip-itinerary.transport-pattern}")
    private String transport;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.trip-itinerary.remark-size}")
    private String remark;
}
