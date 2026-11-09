package com.bluewhale.mall.order.application.service.impl;

import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.order.application.service.MallReportAppService;
import com.bluewhale.mall.order.application.view.DailyReportView;
import com.bluewhale.mall.order.domain.model.DailyReportStat;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 商城运营报表应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MallReportAppServiceImpl implements MallReportAppService {

    private final OrderRepository orderRepository;

    @Override
    public List<DailyReportView> daily(LocalDate from, LocalDate to, MallActor actor) {
        // 报表是运营视角的数据（全租户订单额），买家接口绝不透出
        actor.requireUserId();
        if (!actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, "只有商城运营可以查看运营报表");
        }
        List<DailyReportStat> stats = orderRepository.dailyReport(from, to);
        List<DailyReportView> views = new ArrayList<>(stats.size());
        for (DailyReportStat stat : stats) {
            views.add(toView(stat));
        }
        return views;
    }

    /**
     * 统计行转视图。
     *
     * @param stat 统计行
     * @return 视图
     */
    private DailyReportView toView(DailyReportStat stat) {
        DailyReportView view = new DailyReportView();
        view.setStatDate(stat.getStatDate());
        view.setOrderCount(stat.getOrderCount());
        view.setGoodsAmount(stat.getGoodsAmount());
        view.setFreightAmount(stat.getFreightAmount());
        view.setCouponDiscount(stat.getCouponDiscount());
        view.setPaidAmount(stat.getPaidAmount());
        view.setRefundAmount(stat.getRefundAmount());
        return view;
    }
}
