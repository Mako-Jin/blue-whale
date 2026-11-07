package com.bluewhale.crm.commerce.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.command.ContractRenewalCommand;
import com.bluewhale.crm.commerce.application.view.ContractView;
import com.bluewhale.crm.commerce.domain.model.ContractQuery;

import java.util.List;

/**
 * 合同台账应用服务（M3 / 7f-1）。
 *
 * @author BlueWhale
 */
public interface ContractAppService {

    /**
     * 合同台账分页。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<ContractView> page(ContractQuery query, CrmActor actor);

    /**
     * 合同台账详情。
     *
     * @param id 台账 ID
     * @param actor 当前操作人
     * @return 合同视图
     */
    ContractView detail(Long id, CrmActor actor);

    /**
     * 到期提醒列表。
     *
     * @param days 到期天数窗口；空按配置默认值
     * @param actor 当前操作人
     * @return 合同视图列表
     */
    List<ContractView> expiring(Integer days, CrmActor actor);

    /**
     * 按客户同步合同投影。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 同步后的合同数量
     */
    int syncByCustomer(Long customerId, CrmActor actor);

    /**
     * 定时对账：刷新已有台账客户的合同投影。
     *
     * @return 刷新的合同数量
     */
    int reconcile();

    /**
     * 标记合同续约。
     *
     * @param id 台账 ID
     * @param command 续约命令
     * @param actor 当前操作人
     * @return 合同视图
     */
    ContractView markRenewal(Long id, ContractRenewalCommand command, CrmActor actor);
}
