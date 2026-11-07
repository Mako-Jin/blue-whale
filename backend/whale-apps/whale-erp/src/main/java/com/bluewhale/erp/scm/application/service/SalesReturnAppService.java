package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.SalesReturnCreateCommand;
import com.bluewhale.erp.scm.application.command.SalesReturnInternalCreateCommand;
import com.bluewhale.erp.scm.application.view.SalesReturnView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 销售退货应用服务。
 *
 * @author BlueWhale
 */
public interface SalesReturnAppService {

    /**
     * 分页查询销售退货单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesReturnView> page(ScmQuery query);

    /**
     * 销售退货单详情。
     *
     * @param id 主键
     * @return 视图
     */
    SalesReturnView detail(Long id);

    /**
     * 建单（草稿）。
     *
     * @param command 创建命令
     * @return 视图
     */
    SalesReturnView create(SalesReturnCreateCommand command);

    /**
     * 过账：库存入库、应收冲减、红字凭证。
     *
     * @param id 主键
     * @param operatorId 操作人 ID
     * @return 视图
     */
    SalesReturnView post(Long id, Long operatorId);

    /**
     * 取消草稿。
     *
     * @param id 主键
     * @return 视图
     */
    SalesReturnView cancel(Long id);

    /**
     * CRM 内网创建并立即过账（按 crmReturnNo 幂等）。
     *
     * @param command 内网创建命令
     * @return 视图
     */
    SalesReturnView createInternal(SalesReturnInternalCreateCommand command);

    /**
     * 按 CRM 退换货申请单号查询退货单。
     *
     * @param crmReturnNo CRM 退换货申请单号
     * @return 视图；不存在返回 {@code null}
     */
    SalesReturnView findByCrmReturnNo(String crmReturnNo);
}
