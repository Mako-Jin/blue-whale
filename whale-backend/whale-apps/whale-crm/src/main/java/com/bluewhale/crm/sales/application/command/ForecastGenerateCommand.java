package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 生成销售预测快照命令。
 *
 * @author BlueWhale
 */
@Data
public class ForecastGenerateCommand {

    /** 周期：yyyy-MM 或 yyyy-Qn */
    @NotBlank(message = "{validation.crm.forecast-generate.period-required}")
    @Size(max = 10, message = "{validation.crm.forecast-generate.period-size}")
    private String period;

    /** 周期类型：MONTH / QUARTER，默认 MONTH */
    private String periodType;

    /** 范围类型：USER / DEPT，默认 USER */
    private String scopeType;

    /** 负责人（仅全量数据范围可指定） */
    private Long ownerId;

    /** 部门（DEPT 范围必填） */
    private Long deptId;
}
