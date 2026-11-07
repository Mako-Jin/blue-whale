package com.bluewhale.mall.catalog.application.command;

import lombok.Data;

/**
 * 库存调整命令。
 *
 * <p>用<b>增量</b>而不是「设置为多少」：绝对值设置在并发下会覆盖别人刚写进去的数量
 * （运营 A 设为 100，同时运营 B 设为 120，最终谁后写谁生效，先写的那次调整凭空消失）。
 * 增量是加法，天然可叠加。</p>
 *
 * @author BlueWhale
 */
@Data
public class StockAdjustCommand {

    /** 调整增量，可为负（减少可售） */
    private Integer delta;

    /** 调整原因（留痕用，记入备注） */
    private String remark;
}
