package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.SalesOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.SalesOrderInternalCreateCommand;
import com.bluewhale.erp.scm.application.command.ShipCommand;
import com.bluewhale.erp.scm.application.view.CreditUsageView;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

/**
 * 销售应用服务。
 *
 * @author BlueWhale
 */
public interface SalesAppService {

    /**
     * 分页查询销售订单。
     *
     * @param query 查询条件
     * @return 订单分页（不含分录）
     */
    PageResult<SalesOrderView> page(ScmQuery query);

    /**
     * 查询销售订单详情。
     *
     * @param id 订单 ID
     * @return 订单视图（含分录）
     */
    SalesOrderView detail(Long id);

    /**
     * 新增销售订单（草稿）。
     *
     * @param command 创建命令
     * @return 订单视图
     */
    SalesOrderView create(SalesOrderCreateCommand command);

    /**
     * CRM 商务订单幂等下发（按 {@code crmOrderNo} 去重）。
     *
     * @param command 内网下发命令
     * @return 订单视图（重复请求返回已存在的原单）
     */
    SalesOrderView createFromCrm(SalesOrderInternalCreateCommand command);

    /**
     * 按 CRM 商务订单号查询履约摘要。
     *
     * @param crmOrderNo CRM 商务订单号
     * @return 订单视图；不存在返回 {@code null}
     */
    SalesOrderView findByCrmOrderNo(String crmOrderNo);

    /**
     * 按 ERP 订单号取消订单（重复取消幂等）。
     *
     * @param docNo ERP 订单号
     * @return 订单视图
     */
    SalesOrderView cancelByDocNo(String docNo);

    /**
     * 审核销售订单（校验客户信用额度）。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    SalesOrderView approve(Long id);

    /**
     * 销售发货：出库、生成应收、自动记账。
     *
     * @param id 订单 ID
     * @param command 发货命令
     * @param operatorId 操作人 ID
     * @return 订单视图
     */
    SalesOrderView ship(Long id, ShipCommand command, Long operatorId);

    /**
     * 取消销售订单。
     *
     * @param id 订单 ID
     * @return 订单视图
     */
    SalesOrderView cancel(Long id);

    /**
     * 查询客户信用占用：未结应收 + 在途预占与可用额度。
     *
     * @param customerId 客户主键
     * @return 信用占用视图
     */
    CreditUsageView creditUsage(Long customerId);
}
