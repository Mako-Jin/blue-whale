package com.bluewhale.erp.finance.domain.model;

/**
 * 财务模块状态与类型常量。
 *
 * @author BlueWhale
 */
public final class FinanceStatus {

    /** 凭证草稿 */
    public static final String VOUCHER_DRAFT = "DRAFT";

    /** 凭证已记账 */
    public static final String VOUCHER_POSTED = "POSTED";

    /** 凭证已红冲 */
    public static final String VOUCHER_REVERSED = "REVERSED";

    /** 手工凭证 */
    public static final String TYPE_MANUAL = "MANUAL";

    /** 红冲凭证 */
    public static final String TYPE_REVERSAL = "REVERSAL";

    /** 台账未结 */
    public static final String LEDGER_OPEN = "OPEN";

    /** 台账部分核销 */
    public static final String LEDGER_PARTIALLY_SETTLED = "PARTIALLY_SETTLED";

    /** 台账已结清 */
    public static final String LEDGER_SETTLED = "SETTLED";

    /** 台账已核销坏账 */
    public static final String LEDGER_WRITTEN_OFF = "WRITTEN_OFF";

    /** 银行结算 */
    public static final String SETTLE_BANK = "BANK";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private FinanceStatus() {
    }
}
