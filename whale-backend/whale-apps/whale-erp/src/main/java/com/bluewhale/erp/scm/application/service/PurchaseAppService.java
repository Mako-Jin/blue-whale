package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.ReceiveCommand;
import com.bluewhale.erp.scm.application.view.PurchaseOrderView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 采购应用服务。
 *
 * @author BlueWhale
 */
public interface PurchaseAppService {

    /**
     * 分页查询采购订单。
     *
     * @param query 查询条件
     * @return 订单分页（不含分录）
     */
    PageResult<PurchaseOrderView> page(ScmQuery query);

    /**
     * 查询采购订单详情。
     *
     * @param id 订单 ID
     * @return 订单视图（含分录）
     */
    PurchaseOrderView detail(Long id);

    /**
     * 新增采购订单（草稿）。
     *
     * @param command 创建命令
     * @return 订单视图
     */
    PurchaseOrderView create(PurchaseOrderCreateCommand command);

    /**
     * 审核采购订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    PurchaseOrderView approve(Long id);

    /**
     * 采购收货：入库、生成应付、自动记账。
     *
     * @param id 订单 ID
     * @param command 收货命令
     * @param operatorId 操作人 ID
     * @return 订单视图
     */
    PurchaseOrderView receive(Long id, ReceiveCommand command, Long operatorId);

    /**
     * 取消采购订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    PurchaseOrderView cancel(Long id);
}
