package com.bluewhale.crm.marketing.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.marketing.application.command.NurtureCreateCommand;
import com.bluewhale.crm.marketing.application.command.NurtureStatusCommand;
import com.bluewhale.crm.marketing.application.command.NurtureUpdateCommand;
import com.bluewhale.crm.marketing.application.view.NurtureTaskView;
import com.bluewhale.crm.marketing.domain.model.NurtureQuery;

/**
 * 客户培育任务应用服务。
 *
 * @author BlueWhale
 */
public interface NurtureAppService {

    /**
     * 分页查询培育任务。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<NurtureTaskView> page(NurtureQuery query, CrmActor actor);

    /**
     * 查看培育任务详情。
     *
     * @param id 任务 ID
     * @param actor 当前操作人
     * @return 视图
     */
    NurtureTaskView detail(Long id, CrmActor actor);

    /**
     * 新建培育任务。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 视图
     */
    NurtureTaskView create(NurtureCreateCommand command, CrmActor actor);

    /**
     * 编辑培育任务。
     *
     * @param id 任务 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 视图
     */
    NurtureTaskView update(Long id, NurtureUpdateCommand command, CrmActor actor);

    /**
     * 变更培育任务状态。
     *
     * @param id 任务 ID
     * @param command 状态命令
     * @param actor 当前操作人
     * @return 视图
     */
    NurtureTaskView changeStatus(Long id, NurtureStatusCommand command, CrmActor actor);
}
