package com.bluewhale.erp.finance.application.support;

import com.bluewhale.erp.finance.application.view.AgingView;

import java.math.BigDecimal;

/**
 * 账龄分桶计算。
 *
 * <p>按到期日与今天的差值分桶：未到期、1–30 天、31–60 天、61–90 天、90 天以上。</p>
 *
 * @author BlueWhale
 */
public final class AgingCalculator {

    /** 第一档上限（天） */
    private static final long BUCKET_30 = 30L;

    /** 第二档上限（天） */
    private static final long BUCKET_60 = 60L;

    /** 第三档上限（天） */
    private static final long BUCKET_90 = 90L;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private AgingCalculator() {
    }

    /**
     * 把一笔未结金额累加到对应账龄区间。
     *
     * @param view 汇总视图（各区间初始化为零）
     * @param amount 未结金额
     * @param overdueDays 逾期天数（未到期为负数或零）
     */
    public static void accumulate(AgingView view, BigDecimal amount, long overdueDays) {
        view.setTotal(view.getTotal().add(amount));
        if (overdueDays <= 0) {
            view.setNotDue(view.getNotDue().add(amount));
        } else if (overdueDays <= BUCKET_30) {
            view.setOverdue1To30(view.getOverdue1To30().add(amount));
        } else if (overdueDays <= BUCKET_60) {
            view.setOverdue31To60(view.getOverdue31To60().add(amount));
        } else if (overdueDays <= BUCKET_90) {
            view.setOverdue61To90(view.getOverdue61To90().add(amount));
        } else {
            view.setOverdueOver90(view.getOverdueOver90().add(amount));
        }
    }

    /**
     * 初始化汇总行（各金额为零）。
     *
     * @param dimension 维度（AR / AP）
     * @param partyId 往来单位 ID
     * @param partyName 往来单位名称
     * @return 汇总行
     */
    public static AgingView empty(String dimension, Long partyId, String partyName) {
        AgingView view = new AgingView();
        view.setDimension(dimension);
        view.setPartyId(partyId);
        view.setPartyName(partyName);
        view.setTotal(BigDecimal.ZERO);
        view.setNotDue(BigDecimal.ZERO);
        view.setOverdue1To30(BigDecimal.ZERO);
        view.setOverdue31To60(BigDecimal.ZERO);
        view.setOverdue61To90(BigDecimal.ZERO);
        view.setOverdueOver90(BigDecimal.ZERO);
        return view;
    }
}
