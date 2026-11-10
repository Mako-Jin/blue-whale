package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.erp.mfg.application.view.CrpView;

import java.time.LocalDate;

/**
 * CRP 应用服务。
 *
 * @author BlueWhale
 */
public interface CrpAppService {

    /**
     * 计算产能负荷与工序排程。
     *
     * @param fromDate       开始日期（空则今天）
     * @param toDate         结束日期（空则开始日期 + 29 天）
     * @param workCenterCode 工作中心筛选（空则全部）
     * @return CRP 结果
     */
    CrpView load(LocalDate fromDate, LocalDate toDate, String workCenterCode);
}
