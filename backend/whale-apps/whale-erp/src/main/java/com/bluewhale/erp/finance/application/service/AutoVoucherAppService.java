package com.bluewhale.erp.finance.application.service;

import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;

/**
 * 自动记账应用服务：由业务单据驱动生成并记账，按来源单据幂等。
 *
 * @author BlueWhale
 */
public interface AutoVoucherAppService {

    /**
     * 采购收货记账：借存货、贷应付。
     *
     * @param command 记账命令
     * @return 凭证 ID；已记账时返回已有凭证 ID
     */
    Long postReceipt(AutoVoucherCommand command);

    /**
     * 销售发货记账：借应收、贷收入；同时借成本、贷存货。
     *
     * @param command 记账命令
     * @return 凭证 ID；已记账时返回已有凭证 ID
     */
    Long postShipment(AutoVoucherCommand command);

    /**
     * 库存盘盈/盘亏记账：金额为正记盘盈（借存货、贷待处理财产损溢），
     * 为负记盘亏（借待处理财产损溢、贷存货）。
     *
     * @param command 记账命令，{@code amount} 带符号
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postStockVariance(AutoVoucherCommand command);

    /**
     * 生产领料记账：借生产成本、贷存货。
     *
     * @param command 记账命令
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postProductionIssue(AutoVoucherCommand command);

    /**
     * 生产完工入库记账：借存货、贷生产成本。
     *
     * @param command 记账命令
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postProductionReceipt(AutoVoucherCommand command);

    /**
     * 薪资计提记账：借管理费用、贷应付职工薪酬（金额为应发合计）。
     *
     * @param command 记账命令
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postPayrollAccrual(AutoVoucherCommand command);

    /**
     * 薪资发放记账：借应付职工薪酬（应发合计），贷银行存款（实发）、
     * 应交税费（代扣个税）、其他应付款（代扣社保与公积金）。
     *
     * @param command 记账命令
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postPayrollPayment(AutoVoucherCommand command);

    /**
     * 采购退货记账（红字）：借应付、贷存货。
     *
     * @param command 记账命令
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postPurchaseReturn(AutoVoucherCommand command);

    /**
     * 销售退货记账（红字）：借收入、贷应收；同时借存货、贷成本。
     *
     * @param command 记账命令，{@code costAmount} 为退货入库成本
     * @return 凭证 ID；金额为零或已记账时返回已有凭证 ID / null
     */
    Long postSalesReturn(AutoVoucherCommand command);
}
