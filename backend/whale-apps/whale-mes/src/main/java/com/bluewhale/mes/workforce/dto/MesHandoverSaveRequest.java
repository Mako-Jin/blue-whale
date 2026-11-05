package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 交接班保存请求（新建）
 *
 * @author BlueWhale
 */
@Data
public class MesHandoverSaveRequest {

    /** 交接单号（为空时自动生成） */
    @Size(max = 64, message = "交接单号长度不能超过 64")
    private String handoverNo;

    /** 班组 ID */
    @NotNull(message = "班组不能为空")
    private Long teamId;

    /** 交班班次编码 */
    @NotBlank(message = "交班班次不能为空")
    @Size(max = 64, message = "交班班次编码长度不能超过 64")
    private String fromShiftCode;

    /** 接班班次编码 */
    @NotBlank(message = "接班班次不能为空")
    @Size(max = 64, message = "接班班次编码长度不能超过 64")
    private String toShiftCode;

    /** 交接日期 */
    @NotNull(message = "交接日期不能为空")
    private LocalDate handoverDate;

    /** 交班人 ID */
    private Long fromEmployeeId;

    /** 交班人姓名 */
    @Size(max = 64, message = "交班人姓名长度不能超过 64")
    private String fromEmployeeName;

    /** 接班人 ID */
    private Long toEmployeeId;

    /** 接班人姓名 */
    @Size(max = 64, message = "接班人姓名长度不能超过 64")
    private String toEmployeeName;

    /** 交接内容 */
    @NotBlank(message = "交接内容不能为空")
    @Size(max = 1000, message = "交接内容长度不能超过 1000")
    private String content;

    /** 遗留问题 */
    @Size(max = 1000, message = "遗留问题长度不能超过 1000")
    private String pendingIssues;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
