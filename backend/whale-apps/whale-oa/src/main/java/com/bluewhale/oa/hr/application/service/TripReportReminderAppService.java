package com.bluewhale.oa.hr.application.service;

import java.time.LocalDate;

/**
 * 出差报告提醒应用服务。
 *
 * <p>扫描已通过审批且报告待提交的出差单，向申请人发送站内信提醒；
 * 提醒按出差单与自然日幂等，失败仅记录告警。</p>
 *
 * @author BlueWhale
 */
public interface TripReportReminderAppService {

    /**
     * 提醒逾期未提交报告的出差单。
     *
     * @param deadline 出差结束日期上限（含）
     * @param batchSize 单次最大处理条数
     * @return 成功投递的提醒条数
     */
    int remindOverdueReports(LocalDate deadline, int batchSize);
}
