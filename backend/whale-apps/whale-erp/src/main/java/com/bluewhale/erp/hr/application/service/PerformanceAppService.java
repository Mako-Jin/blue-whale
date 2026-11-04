package com.bluewhale.erp.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.hr.application.command.AppraisalSaveCommand;
import com.bluewhale.erp.hr.application.command.KpiCreateCommand;
import com.bluewhale.erp.hr.application.command.PerformanceCycleCreateCommand;
import com.bluewhale.erp.hr.application.view.PerformanceCycleView;
import com.bluewhale.erp.hr.domain.model.HrQuery;

/**
 * 绩效管理应用服务。
 *
 * @author BlueWhale
 */
public interface PerformanceAppService {

    /**
     * 分页查询绩效周期。
     *
     * @param query 查询条件
     * @return 周期分页
     */
    PageResult<PerformanceCycleView> page(HrQuery query);

    /**
     * 周期详情（含指标与考核结果）。
     *
     * @param id 主键
     * @return 周期
     */
    PerformanceCycleView detail(Long id);

    /**
     * 新建绩效周期。
     *
     * @param command 创建命令
     * @return 周期
     */
    PerformanceCycleView create(PerformanceCycleCreateCommand command);

    /**
     * 新增指标（周期未关闭）。
     *
     * @param id 周期主键
     * @param command 指标命令
     * @return 周期
     */
    PerformanceCycleView addKpi(Long id, KpiCreateCommand command);

    /**
     * 录入考核结果（按周期 + 员工 + 指标 upsert）。
     *
     * @param id 周期主键
     * @param command 考核命令
     * @return 周期
     */
    PerformanceCycleView appraise(Long id, AppraisalSaveCommand command);

    /**
     * 关闭周期。
     *
     * @param id 周期主键
     * @return 周期
     */
    PerformanceCycleView close(Long id);
}
