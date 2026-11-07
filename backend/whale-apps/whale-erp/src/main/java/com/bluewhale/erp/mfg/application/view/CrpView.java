package com.bluewhale.erp.mfg.application.view;

import com.bluewhale.erp.mfg.domain.model.CrpLoadBucket;
import com.bluewhale.erp.mfg.domain.model.CrpResult;
import com.bluewhale.erp.mfg.domain.model.CrpScheduleSlot;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CRP 计算结果视图。
 *
 * @author BlueWhale
 */
@Data
public class CrpView {

    /** 工作中心日负荷 */
    private List<CrpLoadView> loads;

    /** 工序排程 */
    private List<CrpScheduleView> schedule;

    /** 因缺工艺路线或工作中心未维护而排除的订单数 */
    private int excludedOrderCount;

    /** 超出查询区间的工时 */
    private BigDecimal beyondHorizonHours;

    /**
     * 由领域结果构建视图。
     *
     * @param result CRP 结果
     * @return 视图
     */
    public static CrpView of(CrpResult result) {
        CrpView view = new CrpView();
        view.setLoads(result.getLoads().stream().map(CrpView::loadOf).toList());
        view.setSchedule(result.getSchedule().stream().map(CrpView::scheduleOf).toList());
        view.setExcludedOrderCount(result.getExcludedOrderCount());
        view.setBeyondHorizonHours(result.getBeyondHorizonHours());
        return view;
    }

    /**
     * 日负荷转视图。
     *
     * @param bucket 日负荷
     * @return 视图
     */
    private static CrpLoadView loadOf(CrpLoadBucket bucket) {
        CrpLoadView item = new CrpLoadView();
        item.setWorkCenterCode(bucket.getWorkCenterCode());
        item.setDate(bucket.getDate());
        item.setCapacityHours(bucket.getCapacityHours());
        item.setLoadHours(bucket.getLoadHours());
        item.setUtilizationPercent(bucket.getUtilizationPercent());
        item.setOverload(bucket.isOverload());
        return item;
    }

    /**
     * 排程转视图。
     *
     * @param slot 排程
     * @return 视图
     */
    private static CrpScheduleView scheduleOf(CrpScheduleSlot slot) {
        CrpScheduleView item = new CrpScheduleView();
        item.setOrderNo(slot.getOrderNo());
        item.setItemCode(slot.getItemCode());
        item.setStepNo(slot.getStepNo());
        item.setName(slot.getName());
        item.setWorkCenterCode(slot.getWorkCenterCode());
        item.setQuantity(slot.getQuantity());
        item.setHours(slot.getHours());
        item.setStartDate(slot.getStartDate());
        item.setEndDate(slot.getEndDate());
        item.setStatus(slot.getStatus());
        item.setDelayed(slot.isDelayed());
        return item;
    }
}
