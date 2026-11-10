package com.bluewhale.crm.sales.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.sales.application.command.OpportunitySaveCommand;
import com.bluewhale.crm.sales.application.command.OpportunityStageCommand;
import com.bluewhale.crm.sales.application.view.FunnelView;
import com.bluewhale.crm.sales.application.view.OpportunityStageHistoryView;
import com.bluewhale.crm.sales.application.view.OpportunityView;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;

import java.util.List;

/**
 * 商机应用服务（M2）。
 *
 * @author BlueWhale
 */
public interface OpportunityAppService {

    /**
     * 分页查询商机（自动应用数据范围）。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 商机分页
     */
    PageResult<OpportunityView> page(OpportunityQuery query, CrmActor actor);

    /**
     * 查询商机详情。
     *
     * @param id 商机 ID
     * @param actor 当前操作人
     * @return 商机视图
     */
    OpportunityView detail(Long id, CrmActor actor);

    /**
     * 新建商机。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 商机视图
     */
    OpportunityView create(OpportunitySaveCommand command, CrmActor actor);

    /**
     * 编辑商机（未关闭）。
     *
     * @param id 商机 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 商机视图
     */
    OpportunityView update(Long id, OpportunitySaveCommand command, CrmActor actor);

    /**
     * 阶段流转。
     *
     * @param id 商机 ID
     * @param command 流转命令
     * @param actor 当前操作人
     * @return 商机视图
     */
    OpportunityView changeStage(Long id, OpportunityStageCommand command, CrmActor actor);

    /**
     * 查询阶段历史。
     *
     * @param id 商机 ID
     * @param actor 当前操作人
     * @return 阶段历史
     */
    List<OpportunityStageHistoryView> history(Long id, CrmActor actor);

    /**
     * 销售漏斗聚合。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 漏斗视图
     */
    FunnelView funnel(OpportunityQuery query, CrmActor actor);
}
