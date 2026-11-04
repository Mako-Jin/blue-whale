package com.bluewhale.erp.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * ERP 业务错误码。
 *
 * <p>段位约定：{@code 400xx} 参数与业务校验失败，{@code 404xx} 资源不存在
 * （跨租户一律按不存在处理），{@code 409xx} 状态冲突。</p>
 *
 * @author BlueWhale
 */
public final class ErpErrorCode {

    /** 金额非法（必须大于 0 且最多 2 位小数） */
    public static final int AMOUNT_INVALID = 40001;

    /** 科目不存在或已停用 */
    public static final int ACCOUNT_INVALID = 40002;

    /** 科目编码已存在 */
    public static final int ACCOUNT_CODE_CONFLICT = 40901;

    /** 会计期间不存在 */
    public static final int PERIOD_NOT_FOUND = 40401;

    /** 会计期间已关账，禁止记账 */
    public static final int PERIOD_CLOSED = 40902;

    /** 会计期间状态不允许该操作 */
    public static final int PERIOD_STATE_CONFLICT = 40903;

    /** 客户不存在或已停用 */
    public static final int CUSTOMER_INVALID = 40003;

    /** 科目不存在 */
    public static final int ACCOUNT_NOT_FOUND = 40405;

    /** 客户不存在 */
    public static final int CUSTOMER_NOT_FOUND = 40406;

    /** 仓库不存在 */
    public static final int WAREHOUSE_NOT_FOUND = 40407;

    /** 客户编码已存在 */
    public static final int CUSTOMER_CODE_CONFLICT = 40904;

    /** 仓库编码已存在 */
    public static final int WAREHOUSE_CODE_CONFLICT = 40905;

    /** 凭证不存在 */
    public static final int VOUCHER_NOT_FOUND = 40402;

    /** 凭证没有分录 */
    public static final int VOUCHER_LINE_EMPTY = 40004;

    /** 凭证借贷不平衡 */
    public static final int VOUCHER_UNBALANCED = 40005;

    /** 凭证状态不允许该操作 */
    public static final int VOUCHER_STATE_CONFLICT = 40906;

    /** 凭证日期不在会计期间内 */
    public static final int VOUCHER_DATE_INVALID = 40006;

    /** 单据日期非法（如到期日早于单据日期） */
    public static final int DOC_DATE_INVALID = 40007;

    /** 应收单不存在 */
    public static final int RECEIVABLE_NOT_FOUND = 40403;

    /** 应付单不存在 */
    public static final int PAYABLE_NOT_FOUND = 40404;

    /** 单据号已存在 */
    public static final int DOC_NO_CONFLICT = 40907;

    /** 核销金额超过未核销余额 */
    public static final int SETTLE_EXCEEDED = 40908;

    /** 台账已结清或已核销坏账，不能再操作 */
    public static final int LEDGER_CLOSED = 40909;

    /** 坏账计提金额超过未核销余额 */
    public static final int BAD_DEBT_EXCEEDED = 40910;

    /** 物料不存在、已停用或未维护 */
    public static final int ITEM_INVALID = 40011;

    /** 数量非法（必须大于 0 且最多 4 位小数） */
    public static final int QUANTITY_INVALID = 40012;

    /** 单价非法 */
    public static final int PRICE_INVALID = 40013;

    /** 采购订单不存在 */
    public static final int PURCHASE_ORDER_NOT_FOUND = 40408;

    /** 销售订单不存在 */
    public static final int SALES_ORDER_NOT_FOUND = 40409;

    /** 库存记录不存在 */
    public static final int STOCK_NOT_FOUND = 40410;

    /** 供应商不存在或已停用 */
    public static final int SUPPLIER_INVALID = 40014;

    /** 单据状态不允许该操作 */
    public static final int ORDER_STATE_CONFLICT = 40911;

    /** 收货数量超过订单未收数量 */
    public static final int RECEIVE_EXCEEDED = 40912;

    /** 发货数量超过订单未发数量 */
    public static final int SHIP_EXCEEDED = 40913;

    /** 库存不足，禁止负库存 */
    public static final int NEGATIVE_STOCK = 40914;

    /** 客户信用额度不足 */
    public static final int CREDIT_LIMIT_EXCEEDED = 40915;

    /** 调拨单不存在 */
    public static final int TRANSFER_NOT_FOUND = 40411;

    /** 盘点单不存在 */
    public static final int STOCKTAKE_NOT_FOUND = 40412;

    /** 调拨参数非法（同仓调拨或仓库停用） */
    public static final int TRANSFER_INVALID = 40015;

    /** 调拨单状态不允许该操作 */
    public static final int TRANSFER_STATE_CONFLICT = 40916;

    /** 盘点单状态不允许该操作 */
    public static final int STOCKTAKE_STATE_CONFLICT = 40917;

    /** 需求预测不存在 */
    public static final int FORECAST_NOT_FOUND = 40413;

    /** 主生产计划不存在 */
    public static final int MPS_NOT_FOUND = 40414;

    /** 生产订单不存在 */
    public static final int PRODUCTION_ORDER_NOT_FOUND = 40415;

    /** 生产计划参数非法（仓库停用、日期倒挂等） */
    public static final int MFG_INVALID = 40016;

    /** 生产单据状态不允许该操作 */
    public static final int MFG_STATE_CONFLICT = 40918;

    /** 领料数量超过需求数量 */
    public static final int MATERIAL_ISSUE_EXCEEDED = 40919;

    /** 存在未领完的用料，禁止完工入库 */
    public static final int MATERIAL_NOT_ISSUED = 40920;

    /** 完工数量超过计划数量 */
    public static final int PRODUCTION_COMPLETE_EXCEEDED = 40921;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(AMOUNT_INVALID, "金额必须大于 0，且最多保留 2 位小数"),
            Map.entry(ACCOUNT_INVALID, "科目不存在、已停用或不是末级科目"),
            Map.entry(ACCOUNT_CODE_CONFLICT, "科目编码已存在"),
            Map.entry(PERIOD_NOT_FOUND, "会计期间不存在，请先维护期间"),
            Map.entry(PERIOD_CLOSED, "会计期间已关账，禁止记账；如需调整请使用红冲"),
            Map.entry(PERIOD_STATE_CONFLICT, "会计期间状态不允许该操作"),
            Map.entry(CUSTOMER_INVALID, "客户不存在或已停用"),
            Map.entry(ACCOUNT_NOT_FOUND, "科目不存在或无权访问"),
            Map.entry(CUSTOMER_NOT_FOUND, "客户不存在或无权访问"),
            Map.entry(WAREHOUSE_NOT_FOUND, "仓库不存在或无权访问"),
            Map.entry(CUSTOMER_CODE_CONFLICT, "客户编码已存在"),
            Map.entry(WAREHOUSE_CODE_CONFLICT, "仓库编码已存在"),
            Map.entry(VOUCHER_NOT_FOUND, "凭证不存在或无权访问"),
            Map.entry(VOUCHER_LINE_EMPTY, "凭证至少需要一条分录"),
            Map.entry(VOUCHER_UNBALANCED, "凭证借贷不平衡"),
            Map.entry(VOUCHER_STATE_CONFLICT, "凭证当前状态不允许该操作"),
            Map.entry(VOUCHER_DATE_INVALID, "凭证日期不在任何会计期间内"),
            Map.entry(DOC_DATE_INVALID, "单据日期非法，请检查到期日"),
            Map.entry(RECEIVABLE_NOT_FOUND, "应收单不存在或无权访问"),
            Map.entry(PAYABLE_NOT_FOUND, "应付单不存在或无权访问"),
            Map.entry(DOC_NO_CONFLICT, "单据号已存在"),
            Map.entry(SETTLE_EXCEEDED, "核销金额不能超过未核销余额"),
            Map.entry(LEDGER_CLOSED, "台账已结清或已核销坏账，不能继续操作"),
            Map.entry(BAD_DEBT_EXCEEDED, "坏账计提金额不能超过未核销余额"),
            Map.entry(ITEM_INVALID, "物料不存在、已停用或未维护"),
            Map.entry(SUPPLIER_INVALID, "供应商不存在或已停用"),
            Map.entry(QUANTITY_INVALID, "数量必须大于 0，且最多保留 4 位小数"),
            Map.entry(PRICE_INVALID, "单价必须大于 0，且最多保留 2 位小数"),
            Map.entry(PURCHASE_ORDER_NOT_FOUND, "采购订单不存在或无权访问"),
            Map.entry(SALES_ORDER_NOT_FOUND, "销售订单不存在或无权访问"),
            Map.entry(STOCK_NOT_FOUND, "库存记录不存在或无权访问"),
            Map.entry(ORDER_STATE_CONFLICT, "单据状态不允许该操作"),
            Map.entry(RECEIVE_EXCEEDED, "收货数量超过订单未收数量"),
            Map.entry(SHIP_EXCEEDED, "发货数量超过订单未发数量"),
            Map.entry(NEGATIVE_STOCK, "库存不足，禁止负库存"),
            Map.entry(CREDIT_LIMIT_EXCEEDED, "客户信用额度不足"),
            Map.entry(TRANSFER_NOT_FOUND, "调拨单不存在或无权访问"),
            Map.entry(STOCKTAKE_NOT_FOUND, "盘点单不存在或无权访问"),
            Map.entry(TRANSFER_INVALID, "调拨仓库非法：调出与调入仓库必须不同且均可用"),
            Map.entry(TRANSFER_STATE_CONFLICT, "调拨单当前状态不允许该操作"),
            Map.entry(STOCKTAKE_STATE_CONFLICT, "盘点单当前状态不允许该操作"),
            Map.entry(FORECAST_NOT_FOUND, "需求预测不存在或无权访问"),
            Map.entry(MPS_NOT_FOUND, "主生产计划不存在或无权访问"),
            Map.entry(PRODUCTION_ORDER_NOT_FOUND, "生产订单不存在或无权访问"),
            Map.entry(MFG_INVALID, "生产计划参数非法，请检查仓库、日期与物料"),
            Map.entry(MFG_STATE_CONFLICT, "生产单据当前状态不允许该操作"),
            Map.entry(MATERIAL_ISSUE_EXCEEDED, "领料数量超过用料需求数量"),
            Map.entry(MATERIAL_NOT_ISSUED, "存在未领完的用料，禁止完工入库"),
            Map.entry(PRODUCTION_COMPLETE_EXCEEDED, "完工数量超过计划生产数量"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private ErpErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "ERP 操作失败");
    }

    /**
     * 以错误码构造业务异常。
     *
     * @param code 业务错误码
     * @return 业务异常
     */
    public static BizException exception(int code) {
        return new BizException(code, messageOf(code));
    }

    /**
     * 以错误码与自定义消息构造业务异常。
     *
     * @param code 业务错误码
     * @param message 自定义提示语
     * @return 业务异常
     */
    public static BizException exception(int code, String message) {
        return new BizException(code, message);
    }
}
