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
    @NotNull(message = "行程序号不能为空")
    @Min(value = 1, message = "行程序号必须从 1 开始")
    private Integer dayNo;

    /** 行程日期 */
    @NotNull(message = "行程日期不能为空")
    private LocalDate tripDate;

    /** 出发地 */
    @NotBlank(message = "行程出发地不能为空")
    @Size(max = 128, message = "行程出发地长度不能超过 128")
    private String fromPlace;

    /** 到达地 */
    @NotBlank(message = "行程到达地不能为空")
    @Size(max = 128, message = "行程到达地长度不能超过 128")
    private String toPlace;

    /** 交通方式 */
    @Pattern(regexp = "TRAIN|PLANE|CAR|OTHER", message = "行程交通方式仅支持 TRAIN / PLANE / CAR / OTHER")
    private String transport;

    /** 备注 */
    @Size(max = 255, message = "行程备注长度不能超过 255")
    private String remark;
}
