package com.bluewhale.crm.marketing.application.view;

import lombok.Data;

/**
 * 回执回调处理结果。
 *
 * @author BlueWhale
 */
@Data
public class DeliveryReceiptView {

    /** 是否推进了明细状态（幂等：重复回调返回 false） */
    private boolean applied;

    /** 匹配到的明细ID */
    private Long itemId;

    /** 触达任务ID */
    private Long sendId;

    /** 回执后的明细状态 */
    private String status;

    /** 未推进原因（已是最新状态 / 未匹配到明细等） */
    private String reason;

    /**
     * 构造处理结果。
     *
     * @param applied 是否推进
     * @param itemId 明细ID
     * @param sendId 触达任务ID
     * @param status 结果状态
     * @param reason 未推进原因
     * @return 结果
     */
    public static DeliveryReceiptView of(boolean applied, Long itemId, Long sendId, String status, String reason) {
        DeliveryReceiptView view = new DeliveryReceiptView();
        view.setApplied(applied);
        view.setItemId(itemId);
        view.setSendId(sendId);
        view.setStatus(status);
        view.setReason(reason);
        return view;
    }
}
