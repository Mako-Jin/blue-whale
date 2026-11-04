package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.SalesQuotationCreateCommand;
import com.bluewhale.erp.scm.application.view.SalesQuotationView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 销售报价应用服务。
 *
 * @author BlueWhale
 */
public interface SalesQuotationAppService {

    /**
     * 分页查询销售报价单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesQuotationView> page(ScmQuery query);

    /**
     * 销售报价单详情。
     *
     * @param id 主键
     * @return 视图
     */
    SalesQuotationView detail(Long id);

    /**
     * 建单（草稿）。
     *
     * @param command 创建命令
     * @return 视图
     */
    SalesQuotationView create(SalesQuotationCreateCommand command);

    /**
     * 客户接受报价。
     *
     * @param id 主键
     * @return 视图
     */
    SalesQuotationView accept(Long id);

    /**
     * 转销售订单。
     *
     * @param id 主键
     * @return 视图
     */
    SalesQuotationView convert(Long id);

    /**
     * 取消报价单。
     *
     * @param id 主键
     * @return 视图
     */
    SalesQuotationView cancel(Long id);
}
