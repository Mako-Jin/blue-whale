package com.bluewhale.crm.customer.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公海回收执行结果。
 *
 * @author BlueWhale
 */
@Data
public class PoolRecycleResultView {

    /** 回收数量 */
    private int recycledCount;

    /** 规则名称 */
    private String ruleName;

    /** 执行时间 */
    private LocalDateTime executedAt;
}
