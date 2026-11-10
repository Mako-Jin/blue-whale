package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.command.MrpRunCommand;
import com.bluewhale.erp.mfg.application.view.MrpPlannedOrderView;
import com.bluewhale.erp.mfg.application.view.MrpRunView;
import com.bluewhale.erp.mfg.domain.model.MrpQuery;

/**
 * MRP 应用服务：运算、重跑、运行记录与计划订单查询。
 *
 * @author BlueWhale
 */
public interface MrpAppService {

    /**
     * 执行 MRP 运算（同步）。
     *
     * @param command 运算命令
     * @return 运行记录详情
     */
    MrpRunView run(MrpRunCommand command);

    /**
     * 按原输入重跑。
     *
     * @param id 原运行记录 ID
     * @return 新运行记录详情
     */
    MrpRunView rerun(Long id);

    /**
     * 运行记录分页。
     *
     * @param query 查询条件
     * @return 运行记录分页
     */
    PageResult<MrpRunView> pageRuns(MrpQuery query);

    /**
     * 运行记录详情。
     *
     * @param id 主键
     * @return 运行记录详情
     */
    MrpRunView detailRun(Long id);

    /**
     * 计划订单分页。
     *
     * @param query 查询条件
     * @return 计划订单分页
     */
    PageResult<MrpPlannedOrderView> pageOrders(MrpQuery query);

    /**
     * 计划订单详情（含追溯）。
     *
     * @param id 主键
     * @return 计划订单详情
     */
    MrpPlannedOrderView detailOrder(Long id);
}
