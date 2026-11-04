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
}
