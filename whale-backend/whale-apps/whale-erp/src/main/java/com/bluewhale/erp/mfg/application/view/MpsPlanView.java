package com.bluewhale.erp.mfg.application.view;

import com.bluewhale.erp.mfg.domain.model.MpsPlan;
import com.bluewhale.erp.mfg.domain.model.MpsPlanLine;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 主生产计划视图。
 *
 * @author BlueWhale
 */
@Data
public class MpsPlanView {

    /** 主键 */
    private Long id;

    /** 计划单号 */
    private String planNo;

    /** 计划期间 */
    private String period;

    /** 状态 */
    private String status;

    /** 计划总数量 */
    private BigDecimal totalPlanned;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<LineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param plan 主生产计划
     * @return 视图
     */
    public static MpsPlanView of(MpsPlan plan) {
        MpsPlanView view = new MpsPlanView();
        view.setId(plan.getId());
        view.setPlanNo(plan.getPlanNo());
        view.setPeriod(plan.getPeriod());
        view.setStatus(plan.getStatus());
        view.setTotalPlanned(plan.getTotalPlanned());
        view.setRemark(plan.getRemark());
        if (plan.getLines() != null) {
            view.setLines(plan.getLines().stream().map(LineView::of).toList());
        }
        return view;
    }

    /**
     * 计划分录视图。
     */
    @Data
    public static class LineView {

        /** 行号 */
        private Integer lineNo;

        /** 物料主键 */
        private Long itemId;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 计量单位 */
        private String uom;

        /** 计划数量 */
        private BigDecimal plannedQuantity;

        /** 已下达数量 */
        private BigDecimal releasedQuantity;

        /**
         * 由领域对象构建视图。
         *
         * @param line 分录
         * @return 视图
         */
        public static LineView of(MpsPlanLine line) {
            LineView view = new LineView();
            view.setLineNo(line.getLineNo());
            view.setItemId(line.getItemId());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setUom(line.getUom());
            view.setPlannedQuantity(line.getPlannedQuantity());
            view.setReleasedQuantity(line.getReleasedQuantity());
            return view;
        }
    }
}
