package com.bluewhale.erp.analytics.application.command;

import lombok.Data;

import java.time.LocalDate;

/**
 * 驾驶舱刷新命令。
 *
 * @author BlueWhale
 */
@Data
public class CockpitRefreshCommand {

    /** 统计期间 yyyyMM */
    private String periodCode;

    /** 统计截止日期（为空取期间末或当天） */
    private LocalDate statDate;
}
