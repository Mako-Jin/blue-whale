package com.bluewhale.crm.service.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.service.application.command.ReturnApprovalCallbackCommand;
import com.bluewhale.crm.service.application.command.ReturnRequestCommand;
import com.bluewhale.crm.service.application.view.ReturnRequestView;
import com.bluewhale.crm.service.domain.model.ReturnRequestQuery;

/**
 * 退换货应用服务。
 *
 * @author BlueWhale
 */
public interface ReturnRequestAppService {

    /**
     * 分页查询退换货申请。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页结果
     */
    PageResult<ReturnRequestView> page(ReturnRequestQuery query, CrmActor actor);

    /**
     * 查看申请详情。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView detail(Long id, CrmActor actor);

    /**
     * 新建退换货申请（草稿）。
     *
     * @param command 创建命令
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView create(ReturnRequestCommand command, CrmActor actor);

    /**
     * 编辑草稿 / 已驳回申请。
     *
     * @param id 申请 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView update(Long id, ReturnRequestCommand command, CrmActor actor);

    /**
     * 提交审批（审批通过后写入 outbox 待下发 ERP）。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView submit(Long id, CrmActor actor);

    /**
     * 取消申请。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView cancel(Long id, CrmActor actor);

    /**
     * 手动重试下发 ERP。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView retry(Long id, CrmActor actor);

    /**
     * 手动对账单张申请。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请视图
     */
    ReturnRequestView reconcile(Long id, CrmActor actor);

    /**
     * 扫描 outbox 下发待执行申请（定时任务入口）。
     *
     * @return 成功下发条数
     */
    int pushPending();

    /**
     * 按 CRM 单号对账 ERP 退货状态（定时任务入口）。
     *
     * @return 刷新条数
     */
    int reconcileAll();

    /**
     * 处理审批回调（幂等）。
     *
     * @param command 回调命令
     * @return 状态是否发生变化
     */
    boolean handleApprovalCallback(ReturnApprovalCallbackCommand command);
}
