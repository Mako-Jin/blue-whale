package com.bluewhale.crm.customer.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.command.PoolAssignCommand;
import com.bluewhale.crm.customer.application.command.PoolRuleSaveCommand;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.OwnerLogView;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import com.bluewhale.crm.customer.application.view.PoolRuleView;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;

import java.util.List;

/**
 * 客户公海应用服务。
 *
 * @author BlueWhale
 */
public interface CustomerPoolAppService {

    /**
     * 分页查询公海客户。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 公海客户分页
     */
    PageResult<CustomerView> page(CustomerQuery query, CrmActor actor);

    /**
     * 领取公海客户。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView claim(Long customerId, CrmActor actor);

    /**
     * 释放客户到公海。
     *
     * @param customerId 客户 ID
     * @param remark 备注
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView release(Long customerId, String remark, CrmActor actor);

    /**
     * 分配客户负责人。
     *
     * @param customerId 客户 ID
     * @param command 分配命令
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView assign(Long customerId, PoolAssignCommand command, CrmActor actor);

    /**
     * 按规则回收客户到公海。
     *
     * @param actor 当前操作人
     * @return 回收结果
     */
    PoolRecycleResultView recycle(CrmActor actor);

    /**
     * 查询当前公海规则。
     *
     * @param actor 当前操作人
     * @return 规则视图
     */
    PoolRuleView getRule(CrmActor actor);

    /**
     * 保存公海规则。
     *
     * @param command 规则命令
     * @param actor 当前操作人
     * @return 规则视图
     */
    PoolRuleView saveRule(PoolRuleSaveCommand command, CrmActor actor);

    /**
     * 查询客户负责人变更历史。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 留痕列表
     */
    List<OwnerLogView> ownerLogs(Long customerId, CrmActor actor);
}
