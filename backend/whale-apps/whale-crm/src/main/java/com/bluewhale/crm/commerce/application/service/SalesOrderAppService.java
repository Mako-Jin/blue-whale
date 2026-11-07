package com.bluewhale.crm.commerce.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.command.SalesOrderApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.SalesOrderCreateCommand;
import com.bluewhale.crm.commerce.application.command.SalesOrderUpdateCommand;
import com.bluewhale.crm.commerce.application.view.SalesOrderView;
import com.bluewhale.crm.commerce.domain.model.SalesOrderQuery;

/**
 * 商务订单应用服务。
 *
 * @author BlueWhale
 */
public interface SalesOrderAppService {

    /**
     * 分页查询商务订单。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<SalesOrderView> page(SalesOrderQuery query, CrmActor actor);

    /**
     * 查看订单详情。
     *
     * @param id 订单 ID
     * @param actor 当前操作人
     * @return 订单视图
     */
    SalesOrderView detail(Long id, CrmActor actor);

    /**
     * 新建商务订单（支持合同 / 报价来源）。
     *
     * @param command 创建命令
     * @param actor 当前操作人
     * @return 订单视图
     */
    SalesOrderView create(SalesOrderCreateCommand command, CrmActor actor);

    /**
     * 编辑草稿订单。
     *
     * @param id 订单 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 订单视图
     */
    SalesOrderView update(Long id, SalesOrderUpdateCommand command, CrmActor actor);

    /**
     * 提交审批（审批通过后写入 outbox 待下发 ERP）。
     *
     * @param id 订单 ID
     * @param actor 当前操作人
     * @return 订单视图
     */
    SalesOrderView submit(Long id, CrmActor actor);

    /**
     * 取消订单（已下发订单先取消 ERP 原单）。
     *
     * @param id 订单 ID
     * @param actor 当前操作人
     * @return 订单视图
     */
    SalesOrderView cancel(Long id, CrmActor actor);

    /**
     * 手动重试下发。
     *
     * @param id 订单 ID
     * @param actor 当前操作人
     * @return 订单视图
     */
    SalesOrderView retry(Long id, CrmActor actor);

    /**
     * 扫描 outbox 下发待推送订单（定时任务入口）。
     *
     * @return 成功下发条数
     */
    int pushPending();

    /**
     * 按 CRM 单号对账 ERP 履约状态（定时任务入口）。
     *
     * @return 刷新条数
     */
    int reconcile();

    /**
     * 处理审批回调（幂等）。
     *
     * @param command 回调命令
     * @return 状态是否发生变化
     */
    boolean handleApprovalCallback(SalesOrderApprovalCallbackCommand command);
}
