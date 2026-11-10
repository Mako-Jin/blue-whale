package com.bluewhale.mall.order.application.service;

import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.order.application.view.DailyReportView;

import java.time.LocalDate;
import java.util.List;

/**
 * 商城运营报表应用服务。
 *
 * @author BlueWhale
 */
public interface MallReportAppService {

    /**
     * 运营日报表（按日聚合订单头，不建快照表）。
     *
     * <p>from 为空默认 7 天前，to 为空默认当天；from 晚于 to 时返回空列表。</p>
     *
     * @param from  起始日期（含，可空）
     * @param to    截止日期（含，可空）
     * @param actor 操作人
     * @return 按日统计列表
     */
    List<DailyReportView> daily(LocalDate from, LocalDate to, MallActor actor);
}
