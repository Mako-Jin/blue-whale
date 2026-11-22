package com.bluewhale.crm.analytics.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建分析导出命令。
 *
 * @author BlueWhale
 */
@Data
public class AnalyticsExportCreateCommand {

    /** 导出类型：CUSTOMER / OPPORTUNITY / ORDER / COLLECTION / ACTION */
    @NotBlank(message = "{validation.crm.analytics-export.export-type-required}")
    private String exportType;

    /** 筛选条件 JSON，可空 */
    private String filtersJson;
}
