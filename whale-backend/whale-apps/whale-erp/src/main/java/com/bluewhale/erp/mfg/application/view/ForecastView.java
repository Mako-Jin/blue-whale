package com.bluewhale.erp.mfg.application.view;

import com.bluewhale.erp.mfg.domain.model.Forecast;
import com.bluewhale.erp.mfg.domain.model.ForecastLine;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 需求预测视图。
 *
 * @author BlueWhale
 */
@Data
public class ForecastView {

    /** 主键 */
    private Long id;

    /** 预测批次号 */
    private String batchNo;

    /** 预测期间 */
    private String period;

    /** 版本号 */
    private Integer version;

    /** 状态 */
    private String status;

    /** 预测总数量 */
    private BigDecimal totalQuantity;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<LineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param forecast 需求预测
     * @return 视图
     */
    public static ForecastView of(Forecast forecast) {
        ForecastView view = new ForecastView();
        view.setId(forecast.getId());
        view.setBatchNo(forecast.getBatchNo());
        view.setPeriod(forecast.getPeriod());
        view.setVersion(forecast.getVersion());
        view.setStatus(forecast.getStatus());
        view.setTotalQuantity(forecast.getTotalQuantity());
        view.setRemark(forecast.getRemark());
        if (forecast.getLines() != null) {
            view.setLines(forecast.getLines().stream().map(LineView::of).toList());
        }
        return view;
    }

    /**
     * 预测分录视图。
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

        /** 预测数量 */
        private BigDecimal quantity;

        /**
         * 由领域对象构建视图。
         *
         * @param line 分录
         * @return 视图
         */
        public static LineView of(ForecastLine line) {
            LineView view = new LineView();
            view.setLineNo(line.getLineNo());
            view.setItemId(line.getItemId());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setUom(line.getUom());
            view.setQuantity(line.getQuantity());
            return view;
        }
    }
}
