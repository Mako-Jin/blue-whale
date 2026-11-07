package com.bluewhale.crm.service.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.service.application.command.TicketAssignCommand;
import com.bluewhale.crm.service.application.command.TicketCreateCommand;
import com.bluewhale.crm.service.application.command.TicketHandleCommand;
import com.bluewhale.crm.service.application.view.TicketView;
import com.bluewhale.crm.service.domain.model.TicketQuery;

/**
 * 工单应用服务。
 *
 * @author BlueWhale
 */
public interface TicketAppService {

    /**
     * 分页查询工单。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<TicketView> page(TicketQuery query, CrmActor actor);

    /**
     * 查看工单详情。
     *
     * @param id 工单 ID
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView detail(Long id, CrmActor actor);

    /**
     * 新建工单。
     *
     * @param command 建单命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView create(TicketCreateCommand command, CrmActor actor);

    /**
     * 分派处理人。
     *
     * @param id 工单 ID
     * @param command 分派命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView assign(Long id, TicketAssignCommand command, CrmActor actor);

    /**
     * 记录处理进展。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView process(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 追加备注。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView comment(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 暂停工单（等待客户）。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView pause(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 恢复工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView resume(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 升级优先级。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView escalate(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 标记已解决。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView resolve(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 关闭工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView close(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 重开工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView reopen(Long id, TicketHandleCommand command, CrmActor actor);

    /**
     * 取消工单。
     *
     * @param id 工单 ID
     * @param command 动作命令
     * @param actor 当前操作人
     * @return 视图
     */
    TicketView cancel(Long id, TicketHandleCommand command, CrmActor actor);
}
