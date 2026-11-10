package com.bluewhale.mes.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绩效统计汇总请求
 *
 * @author BlueWhale
 */
@Data
public class MesPerformanceAggregateRequest {

    /** 统计层级：DAILY/MONTHLY，默认 MONTHLY */
    @Pattern(regexp = "DAILY|MONTHLY", message = "统计层级不合法")
    private String statLevel;

    /** 期间编码：日 YYYYMMDD，月 YYYYMM */
    @NotBlank(message = "期间编码不能为空")
    private String periodCode;

    /** 班组 ID（可选，为空统计全租户） */
    private Long teamId;
}
