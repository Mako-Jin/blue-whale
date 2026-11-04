package com.bluewhale.erp.finance.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.finance.application.command.PayableCreateCommand;
import com.bluewhale.erp.finance.application.command.ReceivableCreateCommand;
import com.bluewhale.erp.finance.application.command.SettleCommand;
import com.bluewhale.erp.finance.application.view.AgingView;
import com.bluewhale.erp.finance.application.view.PayableView;
import com.bluewhale.erp.finance.application.view.ReceivableView;
import com.bluewhale.erp.finance.domain.model.ArApQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应收应付应用服务。
 *
 * @author BlueWhale
 */
public interface ArApAppService {

    /**
     * 分页查询应收单。
     *
     * @param query 查询条件
     * @return 应收分页
     */
    PageResult<ReceivableView> receivablePage(ArApQuery query);

    /**
     * 新增应收单。
     *
     * @param command 创建命令
     * @return 应收视图
     */
    ReceivableView createReceivable(ReceivableCreateCommand command);

    /**
     * 应收核销。
     *
     * @param id 应收单 ID
     * @param command 核销命令
     * @return 应收视图
     */
    ReceivableView settleReceivable(Long id, SettleCommand command);

    /**
     * 应收坏账计提。
     *
     * @param id 应收单 ID
     * @param command 计提命令
     * @return 应收视图
     */
    ReceivableView provisionBadDebt(Long id, SettleCommand command);

    /**
     * 分页查询应付单。
     *
     * @param query 查询条件
     * @return 应付分页
     */
    PageResult<PayableView> payablePage(ArApQuery query);

    /**
     * 新增应付单。
     *
     * @param command 创建命令
     * @return 应付视图
     */
    PayableView createPayable(PayableCreateCommand command);

    /**
     * 应付付款核销。
     *
     * @param id 应付单 ID
     * @param command 付款命令
     * @return 应付视图
     */
    PayableView settlePayable(Long id, SettleCommand command);

    /**
     * 应收账龄分析。
     *
     * @return 按客户汇总的账龄
     */
    List<AgingView> receivableAging();

    /**
     * 查询客户未结应收合计（信用额度控制用）。
     *
     * @param customerId 客户 ID
     * @return 未结应收合计
     */
    BigDecimal outstandingReceivable(Long customerId);

    /**
     * 应付账龄分析。
     *
     * @return 按供应商汇总的账龄
     */
    List<AgingView> payableAging();
}
