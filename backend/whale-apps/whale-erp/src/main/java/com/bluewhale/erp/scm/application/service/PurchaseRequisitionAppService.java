package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.PurchaseRequisitionCreateCommand;
import com.bluewhale.erp.scm.application.view.PurchaseRequisitionView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 采购申请应用服务。
 *
 * @author BlueWhale
 */
public interface PurchaseRequisitionAppService {

    /**
     * 分页查询采购申请单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseRequisitionView> page(ScmQuery query);

    /**
     * 采购申请单详情。
     *
     * @param id 主键
     * @return 视图
     */
    PurchaseRequisitionView detail(Long id);

    /**
     * 建单（草稿）。
     *
     * @param command 创建命令
     * @return 视图
     */
    PurchaseRequisitionView create(PurchaseRequisitionCreateCommand command);

    /**
     * 审核申请单。
     *
     * @param id 主键
     * @return 视图
     */
    PurchaseRequisitionView approve(Long id);

    /**
     * 转采购订单。
     *
     * @param id 主键
     * @return 视图
     */
    PurchaseRequisitionView convert(Long id);

    /**
     * 取消申请单。
     *
     * @param id 主键
     * @return 视图
     */
    PurchaseRequisitionView cancel(Long id);
}
