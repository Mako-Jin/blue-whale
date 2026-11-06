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
    @NotBlank(message = "出差报告内容不能为空")
    @Size(max = 2048, message = "出差报告内容长度不能超过 2048")
    private String content;
}
