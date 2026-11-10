package com.bluewhale.oa.workbench.application.service;

import com.bluewhale.oa.workbench.application.view.WorkbenchViews;

import java.time.LocalDate;

/**
 * 工作台应用服务。
 *
 * @author BlueWhale
 */
public interface WorkbenchAppService {

    /**
     * 汇总工作台数据（一次请求返回待办、公告、会议、任务与生日）。
     *
     * @param userId 当前用户ID
     * @param username 当前用户账号
     * @param today 业务日期
     * @return 汇总视图
     */
    WorkbenchViews.SummaryView summary(Long userId, String username, LocalDate today);
}
