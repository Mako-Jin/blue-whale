package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.PurchaseReturnCreateCommand;
import com.bluewhale.erp.scm.application.view.PurchaseReturnView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 采购退货应用服务。
 *
 * @author BlueWhale
 */
public interface PurchaseReturnAppService {

    /**
     * 分页查询采购退货单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseReturnView> page(ScmQuery query);

    /**
     * 采购退货单详情。
     *
     * @param id 主键
     * @return 视图
     */
    PurchaseReturnView detail(Long id);

    /**
     * 建单（草稿）。
     *
     * @param command 创建命令
     * @return 视图
     */
    PurchaseReturnView create(PurchaseReturnCreateCommand command);

    /**
     * 过账：库存出库、应付冲减、红字凭证。
     *
     * @param id 主键
     * @param operatorId 操作人 ID
     * @return 视图
     */
    PurchaseReturnView post(Long id, Long operatorId);

    /**
     * 取消草稿。
     *
     * @param id 主键
     * @return 视图
     */
    PurchaseReturnView cancel(Long id);
}
