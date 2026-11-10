package com.bluewhale.crm.customer.application.service;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.command.FollowUpCreateCommand;
import com.bluewhale.crm.customer.application.view.FollowUpView;

/**
 * 客户跟进应用服务（append-only）。
 *
 * @author BlueWhale
 */
public interface CustomerFollowUpAppService {

    /**
     * 分页查询客户跟进记录。
     *
     * @param customerId 客户 ID
     * @param pageQuery 分页参数
     * @param actor 当前操作人
     * @return 跟进记录分页
     */
    PageResult<FollowUpView> page(Long customerId, PageQuery pageQuery, CrmActor actor);

    /**
     * 新增跟进记录。
     *
     * @param command 新增命令
     * @param actor 当前操作人
     * @return 跟进视图
     */
    FollowUpView create(FollowUpCreateCommand command, CrmActor actor);
}
