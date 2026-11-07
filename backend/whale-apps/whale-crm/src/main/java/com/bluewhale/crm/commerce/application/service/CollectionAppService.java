package com.bluewhale.crm.commerce.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.command.CollectionActionCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPlanSaveCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPromiseCommand;
import com.bluewhale.crm.commerce.application.view.CollectionActionView;
import com.bluewhale.crm.commerce.application.view.CollectionPlanView;
import com.bluewhale.crm.commerce.domain.model.CollectionQuery;

import java.util.List;

/**
 * 回款计划应用服务（M3 / 7f-1）。
 *
 * @author BlueWhale
 */
public interface CollectionAppService {

    /**
     * 回款计划分页。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<CollectionPlanView> page(CollectionQuery query, CrmActor actor);

    /**
     * 回款计划详情。
     *
     * @param id 计划 ID
     * @param actor 当前操作人
     * @return 计划视图
     */
    CollectionPlanView detail(Long id, CrmActor actor);

    /**
     * 按合同全量保存回款分期。
     *
     * @param command 保存命令
     * @param actor 当前操作人
     * @return 保存后的分期
     */
    List<CollectionPlanView> savePlan(CollectionPlanSaveCommand command, CrmActor actor);

    /**
     * 登记承诺还款。
     *
     * @param id 计划 ID
     * @param command 承诺命令
     * @param actor 当前操作人
     * @return 计划视图
     */
    CollectionPlanView promise(Long id, CollectionPromiseCommand command, CrmActor actor);

    /**
     * 追加催收记录。
     *
     * @param id 计划 ID
     * @param command 催收命令
     * @param actor 当前操作人
     * @return 催收记录视图
     */
    CollectionActionView addAction(Long id, CollectionActionCommand command, CrmActor actor);

    /**
     * 查询催收记录。
     *
     * @param id 计划 ID
     * @param actor 当前操作人
     * @return 催收记录列表
     */
    List<CollectionActionView> actions(Long id, CrmActor actor);
}
