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
    @NotBlank(message = "{validation.mes.mes-performance-lock.period-code-required}")
    @Pattern(regexp = "\\d{6}", message = "{validation.mes.mes-performance-lock.period-code-pattern}")
    private String periodCode;

    /** 锁定人 */
    @NotBlank(message = "{validation.mes.mes-performance-lock.locked-by-required}")
    @Size(max = 64, message = "{validation.mes.mes-performance-lock.locked-by-size}")
    private String lockedBy;

    /** 班组 ID（可选，为空锁定全租户） */
    private Long teamId;
}
