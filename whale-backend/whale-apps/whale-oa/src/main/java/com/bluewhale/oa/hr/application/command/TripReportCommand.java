package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 出差报告提交命令。
 *
 * @author BlueWhale
 */
@Data
public class TripReportCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 出差报告内容 */
    @NotBlank(message = "{validation.oa.trip-report.content-required}")
    @Size(max = 2048, message = "{validation.oa.trip-report.content-size}")
    private String content;
}
