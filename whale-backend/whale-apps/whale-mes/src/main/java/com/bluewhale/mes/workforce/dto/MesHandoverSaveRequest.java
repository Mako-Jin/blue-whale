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
    @Size(max = 64, message = "{validation.mes.mes-handover.handover-no-size}")
    private String handoverNo;

    /** 班组 ID */
    @NotNull(message = "{validation.mes.mes-handover.team-id-required}")
    private Long teamId;

    /** 交班班次编码 */
    @NotBlank(message = "{validation.mes.mes-handover.from-shift-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-handover.from-shift-code-size}")
    private String fromShiftCode;

    /** 接班班次编码 */
    @NotBlank(message = "{validation.mes.mes-handover.to-shift-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-handover.to-shift-code-size}")
    private String toShiftCode;

    /** 交接日期 */
    @NotNull(message = "{validation.mes.mes-handover.handover-date-required}")
    private LocalDate handoverDate;

    /** 交班人 ID */
    private Long fromEmployeeId;

    /** 交班人姓名 */
    @Size(max = 64, message = "{validation.mes.mes-handover.from-employee-name-size}")
    private String fromEmployeeName;

    /** 接班人 ID */
    private Long toEmployeeId;

    /** 接班人姓名 */
    @Size(max = 64, message = "{validation.mes.mes-handover.to-employee-name-size}")
    private String toEmployeeName;

    /** 交接内容 */
    @NotBlank(message = "{validation.mes.mes-handover.content-required}")
    @Size(max = 1000, message = "{validation.mes.mes-handover.content-size}")
    private String content;

    /** 遗留问题 */
    @Size(max = 1000, message = "{validation.mes.mes-handover.pending-issues-size}")
    private String pendingIssues;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-handover.remark-size}")
    private String remark;
}
