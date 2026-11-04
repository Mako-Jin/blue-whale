package com.bluewhale.crm.sales.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.sales.application.command.SalesTargetSaveCommand;
import com.bluewhale.crm.sales.application.command.SalesTargetUpdateCommand;
import com.bluewhale.crm.sales.application.view.SalesTargetAdjustmentView;
import com.bluewhale.crm.sales.application.view.SalesTargetView;
import com.bluewhale.crm.sales.application.view.TargetCompletionView;
import com.bluewhale.crm.sales.domain.model.SalesTargetQuery;

import java.util.List;

/**
 * 销售目标应用服务。
 *
 * @author BlueWhale
 */
public interface SalesTargetAppService {

    /**
     * 分页查询销售目标。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<SalesTargetView> page(SalesTargetQuery query, CrmActor actor);

    /**
     * 查询销售目标详情。
     *
     * @param id 目标 ID
     * @param actor 当前操作人
     * @return 目标详情
     */
    SalesTargetView detail(Long id, CrmActor actor);

    /**
     * 新建销售目标。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 目标详情
     */
    SalesTargetView create(SalesTargetSaveCommand command, CrmActor actor);

    /**
     * 调整销售目标金额并留痕。
     *
     * @param id 目标 ID
     * @param command 调整命令
     * @param actor 当前操作人
     * @return 目标详情
     */
    SalesTargetView update(Long id, SalesTargetUpdateCommand command, CrmActor actor);

    /**
     * 逻辑删除销售目标。
     *
     * @param id 目标 ID
     * @param actor 当前操作人
     */
    void delete(Long id, CrmActor actor);

    /**
     * 查询销售目标完成情况。
     *
     * @param id 目标 ID
     * @param actor 当前操作人
     * @return 完成情况
     */
    TargetCompletionView completion(Long id, CrmActor actor);

    /**
     * 查询目标调整记录。
     *
     * @param id 目标 ID
     * @param actor 当前操作人
     * @return 调整记录
     */
    List<SalesTargetAdjustmentView> adjustments(Long id, CrmActor actor);
}
