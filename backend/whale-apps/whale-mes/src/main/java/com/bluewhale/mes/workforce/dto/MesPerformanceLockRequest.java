package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 月度绩效锁定请求
 *
 * @author BlueWhale
 */
@Data
public class MesPerformanceLockRequest {

    /** 期间编码：月 YYYYMM */
    @NotBlank(message = "期间编码不能为空")
    @Pattern(regexp = "\\d{6}", message = "期间编码必须为 YYYYMM")
    private String periodCode;

    /** 锁定人 */
    @NotBlank(message = "锁定人不能为空")
    @Size(max = 64, message = "锁定人长度不能超过 64")
    private String lockedBy;

    /** 班组 ID（可选，为空锁定全租户） */
    private Long teamId;
}
